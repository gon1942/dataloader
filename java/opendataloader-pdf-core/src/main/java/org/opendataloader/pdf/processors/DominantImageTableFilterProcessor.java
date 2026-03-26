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
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Suppresses small false-positive tables on pages already represented by a
 * dominant full-page image, such as posters, diagrams, and block schematics.
 */
public final class DominantImageTableFilterProcessor {
    private static final double MIN_PAGE_AREA_RATIO = 0.55;
    private static final double MIN_PAGE_WIDTH_RATIO = 0.75;
    private static final double MIN_PAGE_HEIGHT_RATIO = 0.75;
    private static final double TABLE_CONTAINMENT_PADDING = 8.0;
    private static final double MAX_TABLE_TO_IMAGE_AREA_RATIO = 0.08;
    private static final double MIN_EMPTY_CELL_RATIO = 0.60;
    private static final int MAX_ROWS_OR_COLUMNS = 2;
    private static final int MAX_TEXT_LENGTH = 40;
    private static final int MAX_NON_EMPTY_CELLS = 1;

    private DominantImageTableFilterProcessor() {
    }

    public static List<IObject> filterFalseTables(List<IObject> contents, BoundingBox pageBoundingBox) {
        if (contents == null || contents.isEmpty() || pageBoundingBox == null) {
            return contents;
        }

        ImageChunk dominantImage = findDominantImage(contents, pageBoundingBox);
        if (dominantImage == null) {
            return contents;
        }

        BoundingBox dominantBox = expand(dominantImage.getBoundingBox(), TABLE_CONTAINMENT_PADDING);
        double dominantArea = getArea(dominantImage.getBoundingBox());
        List<IObject> filtered = new ArrayList<>();
        for (IObject content : contents) {
            if (!(content instanceof TableBorder) || shouldKeepTable((TableBorder) content, dominantBox, dominantArea)) {
                filtered.add(content);
            }
        }
        return filtered;
    }

    private static boolean shouldKeepTable(TableBorder table, BoundingBox dominantBox, double dominantArea) {
        if (table.isTextBlock()) {
            return true;
        }
        BoundingBox tableBox = table.getBoundingBox();
        if (tableBox == null || !dominantBox.contains(tableBox)) {
            return true;
        }

        double tableArea = getArea(tableBox);
        if (tableArea > dominantArea * MAX_TABLE_TO_IMAGE_AREA_RATIO) {
            return true;
        }
        if (table.getNumberOfRows() > MAX_ROWS_OR_COLUMNS || table.getNumberOfColumns() > MAX_ROWS_OR_COLUMNS) {
            return true;
        }

        CellStats stats = collectCellStats(table);
        double emptyRatio = stats.totalCells == 0 ? 0.0 : ((double) stats.emptyCells / stats.totalCells);
        boolean sparseTable = emptyRatio >= MIN_EMPTY_CELL_RATIO;
        boolean singleLabelBox = stats.nonEmptyCells <= MAX_NON_EMPTY_CELLS && stats.totalTextLength <= MAX_TEXT_LENGTH;
        return !(sparseTable && singleLabelBox);
    }

    private static CellStats collectCellStats(TableBorder table) {
        CellStats stats = new CellStats();
        for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = table.getRow(rowNumber);
            for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                TableBorderCell cell = row.getCell(colNumber);
                if (cell == null || cell.getRowNumber() != rowNumber || cell.getColNumber() != colNumber) {
                    continue;
                }
                stats.totalCells++;
                String text = collectText(cell.getContents()).trim();
                if (text.isEmpty()) {
                    stats.emptyCells++;
                } else {
                    stats.nonEmptyCells++;
                    stats.totalTextLength += text.length();
                }
            }
        }
        return stats;
    }

    private static String collectText(List<IObject> contents) {
        StringBuilder builder = new StringBuilder();
        for (IObject content : contents) {
            if (content instanceof SemanticTextNode) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(((SemanticTextNode) content).getValue());
            } else if (content instanceof TableBorder) {
                String nested = collectTableText((TableBorder) content);
                if (!nested.isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append(' ');
                    }
                    builder.append(nested);
                }
            }
        }
        return builder.toString();
    }

    private static String collectTableText(TableBorder table) {
        StringBuilder builder = new StringBuilder();
        for (TableBorderRow row : table.getRows()) {
            for (TableBorderCell cell : row.getCells()) {
                if (cell == null) {
                    continue;
                }
                String text = collectText(cell.getContents()).trim();
                if (!text.isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append(' ');
                    }
                    builder.append(text);
                }
            }
        }
        return builder.toString();
    }

    private static ImageChunk findDominantImage(List<IObject> contents, BoundingBox pageBoundingBox) {
        double pageArea = getArea(pageBoundingBox);
        double pageWidth = Math.max(0.0, pageBoundingBox.getRightX() - pageBoundingBox.getLeftX());
        double pageHeight = Math.max(0.0, pageBoundingBox.getTopY() - pageBoundingBox.getBottomY());
        ImageChunk dominant = null;
        double maxArea = 0.0;

        for (IObject content : contents) {
            if (!(content instanceof ImageChunk)) {
                continue;
            }
            BoundingBox imageBox = content.getBoundingBox();
            double imageArea = getArea(imageBox);
            double imageWidth = Math.max(0.0, imageBox.getRightX() - imageBox.getLeftX());
            double imageHeight = Math.max(0.0, imageBox.getTopY() - imageBox.getBottomY());
            if (imageArea < pageArea * MIN_PAGE_AREA_RATIO) {
                continue;
            }
            if (imageWidth < pageWidth * MIN_PAGE_WIDTH_RATIO || imageHeight < pageHeight * MIN_PAGE_HEIGHT_RATIO) {
                continue;
            }
            if (imageArea > maxArea) {
                dominant = (ImageChunk) content;
                maxArea = imageArea;
            }
        }
        return dominant;
    }

    private static BoundingBox expand(BoundingBox box, double padding) {
        BoundingBox expanded = new BoundingBox(box);
        expanded.setLeftX(expanded.getLeftX() - padding);
        expanded.setRightX(expanded.getRightX() + padding);
        expanded.setBottomY(expanded.getBottomY() - padding);
        expanded.setTopY(expanded.getTopY() + padding);
        return expanded;
    }

    private static double getArea(BoundingBox box) {
        if (box == null) {
            return 0.0;
        }
        return Math.max(0.0, box.getRightX() - box.getLeftX()) * Math.max(0.0, box.getTopY() - box.getBottomY());
    }

    private static final class CellStats {
        private int totalCells;
        private int emptyCells;
        private int nonEmptyCells;
        private int totalTextLength;
    }
}
