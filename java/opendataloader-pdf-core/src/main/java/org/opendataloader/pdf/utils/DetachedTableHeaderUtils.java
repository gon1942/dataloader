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
package org.opendataloader.pdf.utils;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class DetachedTableHeaderUtils {

    public static final class DetachedHeaderInfo {
        private final List<Integer> paragraphIndexes;
        private final String[] mergedHeaderCells;

        public DetachedHeaderInfo(List<Integer> paragraphIndexes, String[] mergedHeaderCells) {
            this.paragraphIndexes = List.copyOf(paragraphIndexes);
            this.mergedHeaderCells = Arrays.copyOf(mergedHeaderCells, mergedHeaderCells.length);
        }

        public List<Integer> getParagraphIndexes() {
            return paragraphIndexes;
        }

        public String[] getMergedHeaderCells() {
            return Arrays.copyOf(mergedHeaderCells, mergedHeaderCells.length);
        }
    }

    private static final double MAX_HEADER_DISTANCE = 120.0;
    private static final double MAX_ROW_DISTANCE = 12.0;
    private static final double MIN_HORIZONTAL_OVERLAP_RATIO = 0.45;
    private static final double MAX_HEADER_WIDTH_RATIO = 0.55;
    private static final int MIN_HEADER_ROWS = 1;
    private static final int MIN_HEADER_PARAGRAPHS = 3;
    private static final int MAX_HEADER_NUMERIC_TOKENS = 2;

    private DetachedTableHeaderUtils() {
    }

    public static DetachedHeaderInfo findDetachedHeaderInfo(List<IObject> pageContents, int tableIndex, TableBorder table) {
        BoundingBox tableBox = table.getBoundingBox();
        if (tableBox == null) {
            return null;
        }

        List<Integer> candidates = new ArrayList<>();
        for (int index = tableIndex - 1; index >= 0; index--) {
            IObject candidate = pageContents.get(index);
            if (!(candidate instanceof SemanticParagraph)) {
                continue;
            }
            SemanticParagraph paragraph = (SemanticParagraph) candidate;
            if (!isLikelyTableHeaderParagraph(paragraph)) {
                continue;
            }
            BoundingBox candidateBox = paragraph.getBoundingBox();
            if (candidateBox == null) {
                continue;
            }

            double verticalDistance = candidateBox.getBottomY() - tableBox.getTopY();
            if (verticalDistance < -MAX_ROW_DISTANCE) {
                break;
            }
            if (verticalDistance > MAX_HEADER_DISTANCE) {
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

        candidates.sort(Comparator.comparingInt(Integer::intValue));
        if (candidates.size() < MIN_HEADER_PARAGRAPHS) {
            return null;
        }

        List<List<Integer>> rowIndexes = splitIntoHeaderRows(pageContents, candidates);
        if (rowIndexes.size() < MIN_HEADER_ROWS) {
            return null;
        }

        List<List<SemanticParagraph>> rows = new ArrayList<>();
        for (List<Integer> rowIndexList : rowIndexes) {
            List<SemanticParagraph> row = new ArrayList<>();
            for (Integer rowIndex : rowIndexList) {
                row.add((SemanticParagraph) pageContents.get(rowIndex));
            }
            rows.add(row);
        }
        double[] boundaries = getColumnBoundaries(table);
        if (boundaries == null) {
            return null;
        }

        String[] cells = new String[table.getNumberOfColumns()];
        Arrays.fill(cells, "");
        boolean hasContent = false;
        for (List<SemanticParagraph> headerRow : rows) {
            for (SemanticParagraph paragraph : headerRow) {
                BoundingBox box = paragraph.getBoundingBox();
                int startColumn = findStartColumn(boundaries, box);
                int endColumn = findEndColumn(boundaries, box);
                if (startColumn < 0 || endColumn < startColumn) {
                    continue;
                }
                String value = sanitize(paragraph.getValue());
                if (value.isEmpty()) {
                    continue;
                }
                for (int column = startColumn; column <= endColumn; column++) {
                    cells[column] = appendHeaderText(cells[column], value);
                    hasContent = true;
                }
            }
        }
        return hasContent ? new DetachedHeaderInfo(candidates, cells) : null;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace("\u0000", " ").replace("\n", " ").trim();
    }

    private static boolean isLikelyTableHeaderParagraph(SemanticParagraph paragraph) {
        String normalized = sanitize(paragraph.getValue());
        if (normalized.isEmpty()) {
            return false;
        }
        int numericTokens = 0;
        for (String token : normalized.split("\\s+")) {
            if (token.matches(".*\\d.*")) {
                numericTokens++;
            }
        }
        return numericTokens <= MAX_HEADER_NUMERIC_TOKENS;
    }

    private static List<List<Integer>> splitIntoHeaderRows(List<IObject> pageContents, List<Integer> paragraphIndexes) {
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> currentRow = new ArrayList<>();
        Double currentCenterY = null;
        for (Integer index : paragraphIndexes) {
            BoundingBox box = pageContents.get(index).getBoundingBox();
            double centerY = (box.getTopY() + box.getBottomY()) / 2.0;
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

    private static double[] getColumnBoundaries(TableBorder table) {
        TableBorderRow templateRow = Arrays.stream(table.getRows())
            .max(Comparator.comparingInt(DetachedTableHeaderUtils::countUniqueCells))
            .orElse(null);
        if (templateRow == null) {
            return null;
        }
        double[] boundaries = new double[table.getNumberOfColumns() + 1];
        Arrays.fill(boundaries, Double.NaN);
        for (int column = 0; column < table.getNumberOfColumns(); column++) {
            TableBorderCell cell = templateRow.getCells()[column];
            if (cell == null || cell.getBoundingBox() == null || cell.getColNumber() != column) {
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

    private static int countUniqueCells(TableBorderRow row) {
        int count = 0;
        for (int column = 0; column < row.getCells().length; column++) {
            TableBorderCell cell = row.getCells()[column];
            if (cell != null && cell.getColNumber() == column && cell.getRowNumber() == row.getRowNumber()) {
                count++;
            }
        }
        return count;
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

    private static String appendHeaderText(String existing, String value) {
        if (existing == null || existing.isEmpty()) {
            return value;
        }
        if (existing.contains(value)) {
            return existing;
        }
        return existing + " " + value;
    }

    private static double getHorizontalOverlapRatio(BoundingBox left, BoundingBox right) {
        double overlap = Math.max(0.0,
            Math.min(left.getRightX(), right.getRightX()) - Math.max(left.getLeftX(), right.getLeftX()));
        double width = Math.min(left.getWidth(), right.getWidth());
        return width <= 0.0 ? 0.0 : overlap / width;
    }
}
