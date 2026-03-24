/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fallback table detector for matrix-like pages where bordered table detection
 * does not produce any TableBorder objects, but aligned text rows still form a
 * stable grid. This is intentionally conservative to reduce regressions on
 * pages that already have reliable table extraction.
 */
public final class AlignedTextTableProcessor {

    private static final int MIN_ROWS = 4;
    private static final int MIN_COLUMNS = 4;
    private static final int MIN_MATCHING_ROWS = 3;
    private static final int MIN_LINE_ART_IN_GROUP = 6;
    private static final double MIN_ROW_WIDTH = 250.0;
    private static final double MIN_ALIGNMENT_RATIO = 0.6;
    private static final double MAX_ROW_GAP_RATIO = 2.5;
    private static final double MIN_LINE_WIDTH_RATIO = 0.45;
    private static final double TABLE_REGION_MARGIN = 8.0;

    private AlignedTextTableProcessor() {
    }

    public static List<IObject> detectAlignedTextTables(List<IObject> contents) {
        if (contents == null || contents.isEmpty() || containsTableBorder(contents)) {
            return contents;
        }

        List<RowCandidate> rows = collectRows(contents);
        if (rows.size() < MIN_ROWS) {
            return contents;
        }

        List<IObject> updatedContents = new ArrayList<>(contents);
        boolean changed = false;
        for (List<RowCandidate> group : splitIntoGroups(rows)) {
            TableCandidate candidate = detectTableCandidate(contents, rows, group);
            if (candidate == null) {
                continue;
            }
            applyCandidate(updatedContents, candidate);
            changed = true;
        }
        return changed ? DocumentProcessor.removeNullObjectsFromList(updatedContents) : contents;
    }

    private static boolean containsTableBorder(List<IObject> contents) {
        for (IObject content : contents) {
            if (content instanceof TableBorder) {
                return true;
            }
        }
        return false;
    }

    private static List<RowCandidate> collectRows(List<IObject> contents) {
        List<RowCandidate> rows = new ArrayList<>();
        for (int index = 0; index < contents.size(); index++) {
            IObject content = contents.get(index);
            if (!(content instanceof TextLine)) {
                continue;
            }
            TextLine line = (TextLine) content;
            List<TextChunk> chunks = getVisibleChunks(line);
            if (chunks.isEmpty()) {
                continue;
            }
            rows.add(new RowCandidate(index, line, chunks));
        }
        return rows;
    }

    private static List<List<RowCandidate>> splitIntoGroups(List<RowCandidate> rows) {
        List<List<RowCandidate>> groups = new ArrayList<>();
        List<RowCandidate> currentGroup = new ArrayList<>();
        RowCandidate previous = null;
        for (RowCandidate row : rows) {
            if (previous != null && isGroupBoundary(previous, row)) {
                if (!currentGroup.isEmpty()) {
                    groups.add(currentGroup);
                }
                currentGroup = new ArrayList<>();
            }
            currentGroup.add(row);
            previous = row;
        }
        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }
        return groups;
    }

    private static boolean isGroupBoundary(RowCandidate previous, RowCandidate current) {
        double previousHeight = previous.line.getBoundingBox().getHeight();
        double currentHeight = current.line.getBoundingBox().getHeight();
        double maxGap = Math.max(previousHeight, currentHeight) * MAX_ROW_GAP_RATIO;
        double verticalGap = Math.abs(previous.line.getBottomY() - current.line.getTopY());
        if (verticalGap > maxGap) {
            return true;
        }
        double left = Math.max(previous.line.getLeftX(), current.line.getLeftX());
        double right = Math.min(previous.line.getRightX(), current.line.getRightX());
        double overlap = Math.max(0.0, right - left);
        double minWidth = Math.min(previous.line.getWidth(), current.line.getWidth());
        return minWidth > 0.0 && overlap / minWidth < 0.1;
    }

    private static TableCandidate detectTableCandidate(List<IObject> contents, List<RowCandidate> allRows,
                                                       List<RowCandidate> group) {
        if (group.size() < MIN_ROWS) {
            return null;
        }

        BoundingBox groupBox = unionRows(group);
        if (groupBox == null || groupBox.getWidth() < MIN_ROW_WIDTH) {
            return null;
        }

        List<Double> anchors = collectColumnAnchors(group);
        if (anchors.size() < MIN_COLUMNS) {
            return null;
        }

        int matchingRows = 0;
        for (RowCandidate row : group) {
            if (row.line.getWidth() < groupBox.getWidth() * MIN_LINE_WIDTH_RATIO) {
                continue;
            }
            double ratio = countAlignmentRatio(row.chunks, anchors);
            if (ratio >= MIN_ALIGNMENT_RATIO) {
                matchingRows++;
            }
        }
        if (matchingRows < MIN_MATCHING_ROWS) {
            return null;
        }

        int lineArtCount = countLineArt(contents, groupBox);
        if (lineArtCount < MIN_LINE_ART_IN_GROUP) {
            return null;
        }

        List<RowCandidate> expandedGroup = expandGroup(contents, allRows, group, groupBox);
        if (expandedGroup.size() != group.size()) {
            group = expandedGroup;
            groupBox = unionRows(group);
            anchors = collectColumnAnchors(group);
            if (anchors.size() < MIN_COLUMNS) {
                return null;
            }
        }

        TableBorder table = createTable(group, anchors, groupBox);
        if (table == null) {
            return null;
        }
        table.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        TableBorderProcessor.processTableBorder(table, table.getPageNumber());
        return new TableCandidate(group, groupBox, table);
    }

    private static List<RowCandidate> expandGroup(List<IObject> contents, List<RowCandidate> allRows,
                                                  List<RowCandidate> group, BoundingBox groupBox) {
        List<RowCandidate> expanded = new ArrayList<>(group);
        int start = allRows.indexOf(group.get(0));
        int end = allRows.indexOf(group.get(group.size() - 1));
        while (start > 0) {
            RowCandidate previous = allRows.get(start - 1);
            if (!shouldAbsorbNeighbor(contents, previous, expanded.get(0), groupBox)) {
                break;
            }
            expanded.add(0, previous);
            groupBox = unionRows(expanded);
            start--;
        }
        while (end < allRows.size() - 1) {
            RowCandidate next = allRows.get(end + 1);
            if (!shouldAbsorbNeighbor(contents, next, expanded.get(expanded.size() - 1), groupBox)) {
                break;
            }
            expanded.add(next);
            groupBox = unionRows(expanded);
            end++;
        }
        return expanded;
    }

    private static boolean shouldAbsorbNeighbor(List<IObject> contents, RowCandidate candidate,
                                                RowCandidate anchor, BoundingBox groupBox) {
        double verticalGap = Math.abs(candidate.line.getTopY() - anchor.line.getBottomY());
        double maxGap = Math.max(candidate.line.getBoundingBox().getHeight(),
            anchor.line.getBoundingBox().getHeight()) * (MAX_ROW_GAP_RATIO + 0.5);
        if (verticalGap > maxGap) {
            return false;
        }

        double horizontalOverlap = Math.max(0.0,
            Math.min(candidate.line.getRightX(), groupBox.getRightX()) - Math.max(candidate.line.getLeftX(), groupBox.getLeftX()));
        boolean stronglyAligned = candidate.chunks.size() >= 3
            && horizontalOverlap >= Math.min(candidate.line.getWidth(), groupBox.getWidth()) * 0.4;
        boolean labelLike = candidate.chunks.size() <= 2
            && candidate.line.getLeftX() <= groupBox.getLeftX() + 120.0
            && countLineArt(contents, candidate.line.getBoundingBox()) > 0;
        boolean wideHeaderLike = candidate.line.getWidth() >= groupBox.getWidth() * 0.6
            && countLineArt(contents, candidate.line.getBoundingBox()) > 0;
        return stronglyAligned || labelLike || wideHeaderLike;
    }

    private static BoundingBox unionRows(List<RowCandidate> group) {
        BoundingBox box = null;
        for (RowCandidate row : group) {
            if (box == null) {
                box = new BoundingBox(row.line.getBoundingBox());
            } else {
                box.union(row.line.getBoundingBox());
            }
        }
        return box;
    }

    private static List<Double> collectColumnAnchors(List<RowCandidate> group) {
        List<Double> positions = new ArrayList<>();
        double totalFontSize = 0.0;
        int fontCount = 0;
        for (RowCandidate row : group) {
            if (row.chunks.size() < 3) {
                continue;
            }
            totalFontSize += row.line.getFontSize();
            fontCount++;
            for (TextChunk chunk : row.chunks) {
                positions.add(chunk.getLeftX());
            }
        }
        positions.sort(Comparator.naturalOrder());
        double tolerance = Math.max(12.0, (fontCount == 0 ? 10.0 : totalFontSize / fontCount) * 1.8);
        List<Double> anchors = new ArrayList<>();
        for (double position : positions) {
            if (anchors.isEmpty()) {
                anchors.add(position);
                continue;
            }
            double previous = anchors.get(anchors.size() - 1);
            if (Math.abs(position - previous) <= tolerance) {
                anchors.set(anchors.size() - 1, (previous + position) / 2.0);
            } else {
                anchors.add(position);
            }
        }
        return anchors;
    }

    private static double countAlignmentRatio(List<TextChunk> chunks, List<Double> anchors) {
        if (chunks.isEmpty()) {
            return 0.0;
        }
        int matched = 0;
        double tolerance = 24.0;
        for (TextChunk chunk : chunks) {
            double nearestDistance = Double.MAX_VALUE;
            for (double anchor : anchors) {
                nearestDistance = Math.min(nearestDistance, Math.abs(chunk.getLeftX() - anchor));
            }
            if (nearestDistance <= tolerance) {
                matched++;
            }
        }
        return (double) matched / chunks.size();
    }

    private static int countLineArt(List<IObject> contents, BoundingBox tableBox) {
        int count = 0;
        for (IObject content : contents) {
            if (!(content instanceof LineArtChunk)) {
                continue;
            }
            BoundingBox lineBox = content.getBoundingBox();
            if (isInsideTableRegion(lineBox, tableBox)) {
                count++;
            }
        }
        return count;
    }

    private static TableBorder createTable(List<RowCandidate> group, List<Double> anchors, BoundingBox groupBox) {
        int columns = anchors.size();
        TableBorder table = new TableBorder(group.size(), columns);
        table.setBoundingBox(new BoundingBox(groupBox));

        List<Double> boundaries = createColumnBoundaries(anchors, groupBox);
        for (int rowNumber = 0; rowNumber < group.size(); rowNumber++) {
            RowCandidate rowCandidate = group.get(rowNumber);
            TextLine line = rowCandidate.line;
            TableBorderRow row = new TableBorderRow(rowNumber, columns, 0L);
            row.setBoundingBox(new BoundingBox(line.getBoundingBox()));
            table.getRows()[rowNumber] = row;

            if (shouldSpanWholeRow(rowCandidate, groupBox)) {
                TableBorderCell mergedCell = new TableBorderCell(rowNumber, 0, 1, columns, 0L);
                mergedCell.setBoundingBox(new BoundingBox(line.getBoundingBox()));
                for (TextChunk chunk : rowCandidate.chunks) {
                    mergedCell.addContentObject(chunk);
                }
                for (int column = 0; column < columns; column++) {
                    row.getCells()[column] = mergedCell;
                }
                continue;
            }

            List<List<TextChunk>> columnsChunks = new ArrayList<>();
            for (int column = 0; column < columns; column++) {
                columnsChunks.add(new ArrayList<>());
            }
            for (TextChunk chunk : rowCandidate.chunks) {
                int column = findNearestColumn(chunk.getLeftX(), anchors);
                columnsChunks.get(column).add(chunk);
            }

            for (int column = 0; column < columns; column++) {
                TableBorderCell cell = new TableBorderCell(rowNumber, column, 1, 1, 0L);
                List<TextChunk> cellChunks = columnsChunks.get(column);
                if (cellChunks.isEmpty()) {
                    cell.setBoundingBox(createEmptyCellBox(line.getBoundingBox(), boundaries, column));
                } else {
                    BoundingBox cellBox = new BoundingBox(cellChunks.get(0).getBoundingBox());
                    for (TextChunk chunk : cellChunks) {
                        cell.addContentObject(chunk);
                        cellBox.union(chunk.getBoundingBox());
                    }
                    cell.setBoundingBox(cellBox);
                }
                row.getCells()[column] = cell;
            }
        }

        table.calculateCoordinatesUsingBoundingBoxesOfRowsAndColumns();
        return table;
    }

    private static List<Double> createColumnBoundaries(List<Double> anchors, BoundingBox groupBox) {
        List<Double> boundaries = new ArrayList<>();
        boundaries.add(groupBox.getLeftX());
        for (int index = 0; index < anchors.size() - 1; index++) {
            boundaries.add((anchors.get(index) + anchors.get(index + 1)) / 2.0);
        }
        boundaries.add(groupBox.getRightX());
        return boundaries;
    }

    private static boolean shouldSpanWholeRow(RowCandidate row, BoundingBox groupBox) {
        return row.chunks.size() == 1 && row.line.getWidth() >= groupBox.getWidth() * 0.7;
    }

    private static int findNearestColumn(double x, List<Double> anchors) {
        int nearestIndex = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int index = 0; index < anchors.size(); index++) {
            double distance = Math.abs(x - anchors.get(index));
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = index;
            }
        }
        return nearestIndex;
    }

    private static BoundingBox createEmptyCellBox(BoundingBox rowBox, List<Double> boundaries, int column) {
        return new BoundingBox(rowBox.getPageNumber(), boundaries.get(column), rowBox.getBottomY(),
            boundaries.get(column + 1), rowBox.getTopY());
    }

    private static void applyCandidate(List<IObject> contents, TableCandidate candidate) {
        int insertIndex = candidate.rows.get(0).index;
        contents.set(insertIndex, candidate.table);

        for (int rowIndex = 0; rowIndex < candidate.rows.size(); rowIndex++) {
            if (rowIndex == 0) {
                continue;
            }
            contents.set(candidate.rows.get(rowIndex).index, null);
        }

        for (int index = 0; index < contents.size(); index++) {
            IObject content = contents.get(index);
            if (!(content instanceof LineArtChunk)) {
                continue;
            }
            BoundingBox box = content.getBoundingBox();
            if (isInsideTableRegion(box, candidate.box)) {
                contents.set(index, null);
            }
        }
    }

    private static boolean isInsideTableRegion(BoundingBox contentBox, BoundingBox tableBox) {
        double horizontalOverlap = Math.max(0.0,
            Math.min(contentBox.getRightX(), tableBox.getRightX() + TABLE_REGION_MARGIN)
                - Math.max(contentBox.getLeftX(), tableBox.getLeftX() - TABLE_REGION_MARGIN));
        double verticalOverlap = Math.max(0.0,
            Math.min(contentBox.getTopY(), tableBox.getTopY() + TABLE_REGION_MARGIN)
                - Math.max(contentBox.getBottomY(), tableBox.getBottomY() - TABLE_REGION_MARGIN));
        return horizontalOverlap > 0.0
            && verticalOverlap > 0.0
            && horizontalOverlap >= Math.min(contentBox.getWidth(), tableBox.getWidth()) * 0.5;
    }

    private static List<TextChunk> getVisibleChunks(TextLine line) {
        List<TextChunk> chunks = new ArrayList<>();
        for (TextChunk chunk : line.getTextChunks()) {
            if (chunk.isWhiteSpaceChunk() || chunk.isEmpty()) {
                continue;
            }
            chunks.add(chunk);
        }
        return chunks;
    }

    private static final class RowCandidate {
        private final int index;
        private final TextLine line;
        private final List<TextChunk> chunks;

        private RowCandidate(int index, TextLine line, List<TextChunk> chunks) {
            this.index = index;
            this.line = line;
            this.chunks = chunks;
        }
    }

    private static final class TableCandidate {
        private final List<RowCandidate> rows;
        private final BoundingBox box;
        private final TableBorder table;

        private TableCandidate(List<RowCandidate> rows, BoundingBox box, TableBorder table) {
            this.rows = rows;
            this.box = box;
            this.table = table;
        }
    }
}
