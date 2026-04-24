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
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes false-positive tables caused by underlined footnote text at the
 * bottom of pages. Underline (decorative) lines in PDFs are stored as
 * horizontal line art. The veraPDF line-based table detector may treat these
 * underlines as table borders, forming an artificial grid from footnote text.
 * <p>
 * All three conditions must be true (AND logic) to filter a table:
 * <ol>
 *   <li>Located in the bottom 5% of the page</li>
 *   <li>All non-empty cells contain only red text (textColor = [1.0, 0.0, 0.0])</li>
 *   <li>Empty cell ratio >= 25%</li>
 * </ol>
 * This narrow AND combination ensures zero impact on legitimate tables.
 */
public final class UnderlineFootnoteTableFilterProcessor {
    private static final double RED_R = 1.0;
    private static final double RED_G = 0.0;
    private static final double RED_B = 0.0;
    private static final double COLOR_EPSILON = 0.01;
    private static final double MIN_BOTTOM_RATIO = 0.95;
    private static final double MIN_EMPTY_CELL_RATIO = 0.25;

    private UnderlineFootnoteTableFilterProcessor() {
    }

    public static List<IObject> filterFootnoteTables(List<IObject> contents, BoundingBox pageBoundingBox) {
        if (contents == null || contents.isEmpty() || pageBoundingBox == null) {
            return contents;
        }

        double pageTopY = pageBoundingBox.getTopY();
        double pageBottomY = pageBoundingBox.getBottomY();
        double pageHeight = pageTopY - pageBottomY;
        if (pageHeight <= 0) {
            return contents;
        }

        // Footnote tables are at the top of the PDF coordinate space
        // (near the end of the reading order), within the top 5% of the page height
        double topThreshold = pageTopY - pageHeight * (1.0 - MIN_BOTTOM_RATIO);

        List<IObject> filtered = new ArrayList<>();
        for (IObject content : contents) {
            if (content instanceof TableBorder) {
                TableBorder table = (TableBorder) content;
                if (!shouldKeepTable(table, topThreshold)) {
                    // Preserve text content: extract text nodes from each row
                    List<IObject> extractedText = extractTextFromTable(table);
                    filtered.addAll(extractedText);
                    continue;
                }
            }
            filtered.add(content);
        }
        return filtered;
    }

    /**
     * Extracts text from each table row as a single SemanticParagraph per row,
     * merging text chunks from all cells in the row to produce readable footnote lines.
     */
    private static List<IObject> extractTextFromTable(TableBorder table) {
        List<IObject> paragraphs = new ArrayList<>();
        for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = table.getRow(rowNumber);
            TextLine mergedLine = new TextLine();
            boolean hasText = false;
            for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                TableBorderCell cell = row.getCell(colNumber);
                if (cell == null || cell.getRowNumber() != rowNumber || cell.getColNumber() != colNumber) {
                    continue;
                }
                List<IObject> cellContents = cell.getContents();
                if (cellContents == null || cellContents.isEmpty()) {
                    continue;
                }
                for (IObject cellContent : cellContents) {
                    if (cellContent instanceof SemanticTextNode) {
                        SemanticTextNode textNode = (SemanticTextNode) cellContent;
                        String value = textNode.getValue();
                        if (value != null && !value.trim().isEmpty()) {
                            // Extract TextChunks from the text node's lines
                            for (int lineIdx = 0; lineIdx < textNode.getLinesNumber(); lineIdx++) {
                                org.verapdf.wcag.algorithms.entities.content.TextLine line =
                                        textNode.getNonSpaceLine(lineIdx);
                                if (line != null) {
                                    for (TextChunk chunk : line.getTextChunks()) {
                                        mergedLine.add(chunk);
                                        hasText = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (hasText) {
                SemanticParagraph paragraph = new SemanticParagraph();
                paragraph.add(mergedLine);
                paragraphs.add(paragraph);
            }
        }
        return paragraphs;
    }

    private static boolean shouldKeepTable(TableBorder table, double topThreshold) {
        BoundingBox tableBox = table.getBoundingBox();
        if (tableBox == null) {
            return true;
        }

        // Keep table if its top edge is NOT in the top 5% of the page
        if (tableBox.getTopY() < topThreshold) {
            return true;
        }

        CellStats stats = collectCellStats(table);
        if (stats.totalCells == 0) {
            return true;
        }

        double emptyRatio = (double) stats.emptyCells / stats.totalCells;
        if (emptyRatio < MIN_EMPTY_CELL_RATIO) {
            return true;
        }

        if (!stats.allTextRed || stats.textCellCount == 0) {
            return true;
        }

        return false;
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
                if (hasTextContent(cell)) {
                    stats.textCellCount++;
                    if (!isAllRedText(cell)) {
                        stats.allTextRed = false;
                    }
                } else {
                    stats.emptyCells++;
                }
            }
        }
        return stats;
    }

    private static boolean hasTextContent(TableBorderCell cell) {
        List<IObject> contents = cell.getContents();
        if (contents == null || contents.isEmpty()) {
            return false;
        }
        for (IObject content : contents) {
            if (content instanceof SemanticTextNode) {
                SemanticTextNode textNode = (SemanticTextNode) content;
                String value = textNode.getValue();
                if (value != null && !value.trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isAllRedText(TableBorderCell cell) {
        List<IObject> contents = cell.getContents();
        if (contents == null || contents.isEmpty()) {
            return true;
        }
        for (IObject content : contents) {
            if (content instanceof SemanticTextNode) {
                SemanticTextNode textNode = (SemanticTextNode) content;
                String value = textNode.getValue();
                if (value != null && !value.trim().isEmpty()) {
                    double[] color = textNode.getTextColor();
                    if (color == null || color.length < 3 || !isRed(color)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isRed(double[] color) {
        return Math.abs(color[0] - RED_R) < COLOR_EPSILON
                && Math.abs(color[1] - RED_G) < COLOR_EPSILON
                && Math.abs(color[2] - RED_B) < COLOR_EPSILON;
    }

    private static final class CellStats {
        private int totalCells;
        private int emptyCells;
        private int textCellCount;
        private boolean allTextRed = true;
    }
}
