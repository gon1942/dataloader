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

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Merges detached paragraph-based header rows back into the table directly below them.
 * This primarily targets reports where the upper header matrix is visually part of the table
 * but is emitted as standalone paragraphs during semantic parsing.
 */
public final class TableHeaderMergeProcessor {

    private static final double MAX_HEADER_DISTANCE = 160.0;
    private static final double MAX_ROW_DISTANCE = 12.0;
    private static final double MIN_HORIZONTAL_OVERLAP_RATIO = 0.45;
    private static final double MAX_HEADER_WIDTH_RATIO = 0.9;
    private static final double MAX_HEADER_FONT_RATIO = 1.8;
    private static final int MIN_HEADER_ROWS = 2;
    private static final int MIN_HEADER_PARAGRAPHS = 4;
    private static final int MAX_HEADER_NUMERIC_TOKENS = 2;

    private TableHeaderMergeProcessor() {
    }

    public static List<IObject> mergeDetachedHeaders(List<IObject> pageContents) {
        if (pageContents == null || pageContents.isEmpty()) {
            return pageContents;
        }

        List<IObject> updated = new ArrayList<>(pageContents);
        boolean changed = false;
        for (int index = 0; index < updated.size(); index++) {
            IObject object = updated.get(index);
            if (!(object instanceof TableBorder)) {
                continue;
            }

            TableBorder table = (TableBorder) object;
            List<Integer> headerIndexes = findHeaderParagraphIndexes(updated, index, table);
            if (headerIndexes.isEmpty()) {
                continue;
            }

            TableBorder merged = mergeHeadersIntoTable(updated, table, headerIndexes);
            if (merged == null) {
                continue;
            }

            updated.set(index, merged);
            for (Integer headerIndex : headerIndexes) {
                updated.set(headerIndex, null);
            }
            changed = true;
        }

        return changed ? DocumentProcessor.removeNullObjectsFromList(updated) : pageContents;
    }

    private static List<Integer> findHeaderParagraphIndexes(List<IObject> contents, int tableIndex, TableBorder table) {
        BoundingBox tableBox = table.getBoundingBox();
        if (tableBox == null) {
            return List.of();
        }
        double referenceFontSize = getReferenceFontSize(table);

        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < contents.size(); index++) {
            if (index == tableIndex) {
                continue;
            }
            IObject candidate = contents.get(index);
            if (candidate == null || candidate instanceof LineArtChunk) {
                continue;
            }
            BoundingBox candidateBox = candidate.getBoundingBox();
            if (candidateBox == null) {
                continue;
            }

            double verticalDistance = candidateBox.getBottomY() - tableBox.getTopY();
            if (verticalDistance < -MAX_ROW_DISTANCE) {
                continue;
            }
            if (verticalDistance > MAX_HEADER_DISTANCE) {
                continue;
            }
            if (!(candidate instanceof SemanticParagraph)) {
                continue;
            }
            SemanticParagraph paragraph = (SemanticParagraph) candidate;
            if (!isLikelyHeaderParagraph(paragraph)) {
                continue;
            }
            if (referenceFontSize > 0.0 && paragraph.getFontSize() > referenceFontSize * MAX_HEADER_FONT_RATIO) {
                continue;
            }
            if (getHorizontalOverlapRatio(candidateBox, tableBox) < MIN_HORIZONTAL_OVERLAP_RATIO) {
                continue;
            }
            if (tableBox.getWidth() > 0.0 && candidateBox.getWidth() / tableBox.getWidth() > MAX_HEADER_WIDTH_RATIO) {
                continue;
            }
            candidates.add(index);
        }

        candidates.sort(Comparator
            .comparingDouble((Integer index) -> getCenterY(contents.get(index).getBoundingBox()))
            .reversed()
            .thenComparingDouble(index -> contents.get(index).getBoundingBox().getLeftX()));
        if (candidates.size() < MIN_HEADER_PARAGRAPHS) {
            return List.of();
        }

        List<List<Integer>> headerRows = splitIntoRows(contents, candidates);
        if (headerRows.size() < MIN_HEADER_ROWS) {
            return List.of();
        }

        List<Integer> result = new ArrayList<>();
        for (List<Integer> row : headerRows) {
            result.addAll(row);
        }
        return result;
    }

    private static double getCenterY(BoundingBox box) {
        return (box.getTopY() + box.getBottomY()) / 2.0;
    }

    private static List<List<Integer>> splitIntoRows(List<IObject> contents, List<Integer> paragraphIndexes) {
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> currentRow = new ArrayList<>();
        Double currentCenterY = null;
        for (Integer index : paragraphIndexes) {
            BoundingBox box = contents.get(index).getBoundingBox();
            double centerY = getCenterY(box);
            if (currentCenterY != null && Math.abs(currentCenterY - centerY) > MAX_ROW_DISTANCE) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
            }
            currentRow.add(index);
            currentCenterY = centerY;
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }
        return rows;
    }

    private static TableBorder mergeHeadersIntoTable(List<IObject> contents, TableBorder table, List<Integer> headerIndexes) {
        double[] boundaries = getColumnBoundaries(table);
        if (boundaries == null) {
            return null;
        }

        List<List<Integer>> headerRows = splitIntoRows(contents, headerIndexes);
        int headerRowCount = headerRows.size();
        int rowCount = table.getNumberOfRows() + headerRowCount;
        int colCount = table.getNumberOfColumns();

        TableBorder merged = new TableBorder(rowCount, colCount);
        merged.setRecognizedStructureId(table.getRecognizedStructureId());
        TableBorderRow[] rows = merged.getRows();
        for (int rowNumber = 0; rowNumber < rowCount; rowNumber++) {
            rows[rowNumber] = new TableBorderRow(rowNumber, colCount, null);
        }

        BoundingBox mergedBox = new BoundingBox(table.getBoundingBox());

        for (int headerRowNumber = 0; headerRowNumber < headerRows.size(); headerRowNumber++) {
            List<Integer> headerRow = headerRows.get(headerRowNumber);
            TableBorderRow row = rows[headerRowNumber];
            BoundingBox rowBox = null;
            for (Integer paragraphIndex : headerRow) {
                SemanticParagraph paragraph = (SemanticParagraph) contents.get(paragraphIndex);
                BoundingBox paragraphBox = paragraph.getBoundingBox();
                int startColumn = findStartColumn(boundaries, paragraphBox);
                int endColumn = findEndColumn(boundaries, paragraphBox);
                if (startColumn < 0 || endColumn < startColumn) {
                    continue;
                }

                TableBorderCell cell = new TableBorderCell(headerRowNumber, startColumn, 1,
                    endColumn - startColumn + 1, null);
                cell.addContentObject(paragraph);
                cell.setBoundingBox(new BoundingBox(paragraphBox));
                for (int column = startColumn; column <= endColumn; column++) {
                    row.getCells()[column] = cell;
                }
                if (rowBox == null) {
                    rowBox = new BoundingBox(paragraphBox);
                } else {
                    rowBox.union(paragraphBox);
                }
            }
            fillMissingCells(row, headerRowNumber, boundaries, rowBox);
            if (rowBox != null) {
                row.setBoundingBox(rowBox);
                mergedBox.union(rowBox);
            }
        }

        copyTableBody(table, rows, headerRowCount, boundaries);
        merged.setBoundingBox(mergedBox);
        return merged;
    }

    private static boolean isLikelyHeaderParagraph(SemanticParagraph paragraph) {
        String value = sanitize(paragraph.getValue());
        if (value.isEmpty()) {
            return false;
        }
        int numericTokens = 0;
        for (String token : value.split("\\s+")) {
            if (token.matches(".*\\d.*")) {
                numericTokens++;
            }
        }
        return numericTokens <= MAX_HEADER_NUMERIC_TOKENS;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\u0000', ' ').replace('\n', ' ').trim();
    }

    private static double getReferenceFontSize(TableBorder table) {
        double maxFontSize = 0.0;
        for (TableBorderRow row : table.getRows()) {
            if (row == null) {
                continue;
            }
            for (TableBorderCell cell : row.getCells()) {
                if (cell == null || cell.getContents().isEmpty()) {
                    continue;
                }
                for (IObject content : cell.getContents()) {
                    if (content instanceof SemanticParagraph) {
                        maxFontSize = Math.max(maxFontSize, ((SemanticParagraph) content).getFontSize());
                    }
                }
            }
        }
        return maxFontSize;
    }

    private static void copyTableBody(TableBorder source, TableBorderRow[] targetRows, int rowOffset, double[] boundaries) {
        for (int sourceRowNumber = 0; sourceRowNumber < source.getNumberOfRows(); sourceRowNumber++) {
            TableBorderRow sourceRow = source.getRow(sourceRowNumber);
            TableBorderRow targetRow = targetRows[sourceRowNumber + rowOffset];
            if (sourceRow.getBoundingBox() != null) {
                targetRow.setBoundingBox(new BoundingBox(sourceRow.getBoundingBox()));
            }
            for (int columnNumber = 0; columnNumber < source.getNumberOfColumns(); columnNumber++) {
                TableBorderCell sourceCell = sourceRow.getCells()[columnNumber];
                if (sourceCell == null) {
                    continue;
                }
                if (sourceCell.getColNumber() != columnNumber || sourceCell.getRowNumber() != sourceRow.getRowNumber()) {
                    continue;
                }
                TableBorderCell copiedCell = new TableBorderCell(sourceCell.getRowNumber() + rowOffset,
                    sourceCell.getColNumber(), sourceCell.getRowSpan(), sourceCell.getColSpan(), null);
                if (sourceCell.getBoundingBox() != null) {
                    copiedCell.setBoundingBox(new BoundingBox(sourceCell.getBoundingBox()));
                }
                for (IObject content : sourceCell.getContents()) {
                    copiedCell.addContentObject(content);
                }
                for (int rowNumber = copiedCell.getRowNumber();
                     rowNumber < copiedCell.getRowNumber() + copiedCell.getRowSpan();
                     rowNumber++) {
                    for (int col = copiedCell.getColNumber();
                         col < copiedCell.getColNumber() + copiedCell.getColSpan();
                         col++) {
                        targetRows[rowNumber].getCells()[col] = copiedCell;
                    }
                }
            }
            fillMissingCells(targetRow, sourceRowNumber + rowOffset, boundaries, targetRow.getBoundingBox());
        }
    }

    private static void fillMissingCells(TableBorderRow row, int rowNumber, double[] boundaries, BoundingBox fallbackRowBox) {
        for (int col = 0; col < row.getCells().length; col++) {
            if (row.getCells()[col] != null) {
                continue;
            }
            TableBorderCell emptyCell = new TableBorderCell(rowNumber, col, 1, 1, null);
            emptyCell.setBoundingBox(createCellBoundingBox(boundaries, col, fallbackRowBox));
            row.getCells()[col] = emptyCell;
        }
    }

    private static BoundingBox createCellBoundingBox(double[] boundaries, int column, BoundingBox rowBox) {
        double left = boundaries[column];
        double right = boundaries[column + 1];
        double top = rowBox != null ? rowBox.getTopY() : 0.0;
        double bottom = rowBox != null ? rowBox.getBottomY() : 0.0;
        int pageNumber = rowBox != null ? rowBox.getPageNumber() : 0;
        return new BoundingBox(pageNumber, left, bottom, right, top);
    }

    private static double[] getColumnBoundaries(TableBorder table) {
        TableBorderRow templateRow = Arrays.stream(table.getRows())
            .max(Comparator.comparingInt(TableHeaderMergeProcessor::countUniqueCells))
            .orElse(null);
        if (templateRow == null || countUniqueCells(templateRow) < table.getNumberOfColumns() / 2) {
            return null;
        }

        double[] boundaries = new double[table.getNumberOfColumns() + 1];
        Arrays.fill(boundaries, Double.NaN);
        for (int column = 0; column < table.getNumberOfColumns(); column++) {
            TableBorderCell cell = templateRow.getCells()[column];
            if (cell == null || cell.getBoundingBox() == null) {
                continue;
            }
            if (cell.getColNumber() != column) {
                continue;
            }
            boundaries[column] = cell.getBoundingBox().getLeftX();
            boundaries[column + cell.getColSpan()] = cell.getBoundingBox().getRightX();
        }
        if (Double.isNaN(boundaries[0]) || Double.isNaN(boundaries[boundaries.length - 1])) {
            return null;
        }
        interpolateBoundaries(boundaries);
        return boundaries;
    }

    private static void interpolateBoundaries(double[] boundaries) {
        int previousKnown = 0;
        for (int index = 1; index < boundaries.length; index++) {
            if (Double.isNaN(boundaries[index])) {
                continue;
            }
            int gap = index - previousKnown;
            if (gap > 1) {
                double step = (boundaries[index] - boundaries[previousKnown]) / gap;
                for (int fill = previousKnown + 1; fill < index; fill++) {
                    boundaries[fill] = boundaries[previousKnown] + step * (fill - previousKnown);
                }
            }
            previousKnown = index;
        }
    }

    private static int countUniqueCells(TableBorderRow row) {
        Set<TableBorderCell> unique = new LinkedHashSet<>();
        for (int column = 0; column < row.getCells().length; column++) {
            TableBorderCell cell = row.getCells()[column];
            if (cell != null && cell.getColNumber() == column && cell.getRowNumber() == row.getRowNumber()) {
                unique.add(cell);
            }
        }
        return unique.size();
    }

    private static int findStartColumn(double[] boundaries, BoundingBox box) {
        for (int column = 0; column < boundaries.length - 1; column++) {
            if (box.getRightX() > boundaries[column] && box.getLeftX() < boundaries[column + 1]) {
                return column;
            }
        }
        return -1;
    }

    private static int findEndColumn(double[] boundaries, BoundingBox box) {
        for (int column = boundaries.length - 2; column >= 0; column--) {
            if (box.getRightX() > boundaries[column] && box.getLeftX() < boundaries[column + 1]) {
                return column;
            }
        }
        return -1;
    }

    private static double getHorizontalOverlapRatio(BoundingBox left, BoundingBox right) {
        double overlap = Math.max(0.0, Math.min(left.getRightX(), right.getRightX()) - Math.max(left.getLeftX(), right.getLeftX()));
        double width = Math.min(left.getWidth(), right.getWidth());
        return width <= 0.0 ? 0.0 : overlap / width;
    }
}
