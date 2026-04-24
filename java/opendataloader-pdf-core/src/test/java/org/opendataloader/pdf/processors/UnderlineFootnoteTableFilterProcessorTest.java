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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

class UnderlineFootnoteTableFilterProcessorTest {

    @BeforeEach
    void setUp() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
    }

    @Test
    void removesFootnoteTable_atBottom_allRed_highEmptyRatio() {
        // Page height = 1512 (matches input3.pdf). Bottom 5% threshold = y >= 1436.4
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 1224.0, 1512.0);
        List<IObject> contents = new ArrayList<>();

        // Footnote table at bottom: y range 1384-1438 (bottomY=1384 >= 1436.4 threshold)
        contents.add(createFootnoteTable(new BoundingBox(1, 80.928, 1384.735, 466.189, 1437.995)));
        contents.add(new SemanticParagraph());

        List<IObject> filtered = UnderlineFootnoteTableFilterProcessor.filterFootnoteTables(contents, pageBox);

        // Table should be removed and replaced by extracted text paragraphs
        boolean hasTableBorder = false;
        boolean hasParagraphs = false;
        for (IObject obj : filtered) {
            if (obj instanceof TableBorder) {
                hasTableBorder = true;
            }
            if (obj instanceof SemanticParagraph && !((SemanticParagraph) obj).isEmpty()) {
                hasParagraphs = true;
            }
        }
        Assertions.assertFalse(hasTableBorder, "Footnote table should be removed");
        Assertions.assertTrue(hasParagraphs, "Text paragraphs should be extracted from removed table");
        Assertions.assertTrue(filtered.get(filtered.size() - 1) instanceof SemanticParagraph, "Other content should be preserved");
    }

    @Test
    void removesFootnoteTable_preservesAllTextContent() {
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 1224.0, 1512.0);
        List<IObject> contents = new ArrayList<>();

        // Create footnote table with specific text content
        TableBorder table = createFootnoteTableWithText(
                new BoundingBox(1, 80.928, 1384.735, 466.189, 1437.995),
                "재고조정금액 #1", "재고실사 적기처리율");
        contents.add(table);

        List<IObject> filtered = UnderlineFootnoteTableFilterProcessor.filterFootnoteTables(contents, pageBox);

        // Verify no TableBorder remains
        for (IObject obj : filtered) {
            Assertions.assertFalse(obj instanceof TableBorder, "No TableBorder should remain");
        }

        // Verify text is preserved
        StringBuilder allText = new StringBuilder();
        for (IObject obj : filtered) {
            if (obj instanceof SemanticTextNode) {
                String value = ((SemanticTextNode) obj).getValue();
                if (value != null) {
                    allText.append(value);
                }
            }
        }
        Assertions.assertTrue(allText.toString().contains("재고조정금액 #1"),
                "Footnote text '재고조정금액 #1' should be preserved");
        Assertions.assertTrue(allText.toString().contains("재고실사 적기처리율"),
                "Footnote text '재고실사 적기처리율' should be preserved");
    }

    @Test
    void keepsTable_withPartialRedText_inMiddleOfPage() {
        // Table in the middle of the page with some red cells (e.g., negative financial values)
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 1224.0, 1512.0);
        List<IObject> contents = new ArrayList<>();

        // Table at y=600 (middle of page, NOT in bottom 5%)
        contents.add(createMixedColorTable(new BoundingBox(1, 80.0, 600.0, 500.0, 800.0)));

        List<IObject> filtered = UnderlineFootnoteTableFilterProcessor.filterFootnoteTables(contents, pageBox);

        Assertions.assertEquals(1, filtered.size());
        Assertions.assertTrue(filtered.get(0) instanceof TableBorder);
    }

    @Test
    void keepsTable_inTop5Percent_withLowEmptyRatio() {
        // A table at the top 5% of the page but with very few empty cells (0%)
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 1224.0, 1512.0);
        List<IObject> contents = new ArrayList<>();

        // Dense table at top 5%: all cells filled, 0% empty
        contents.add(createDenseTableAtTop5Percent(new BoundingBox(1, 80.0, 1384.0, 500.0, 1438.0)));

        List<IObject> filtered = UnderlineFootnoteTableFilterProcessor.filterFootnoteTables(contents, pageBox);

        Assertions.assertEquals(1, filtered.size());
        Assertions.assertTrue(filtered.get(0) instanceof TableBorder);
    }

    @Test
    void keepsTable_inTop5Percent_allRedButLowEmptyRatio() {
        // Table at top 5%, all red text, but very few empty cells (0%)
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 1224.0, 1512.0);
        List<IObject> contents = new ArrayList<>();

        // All-red dense table at top 5%
        contents.add(createAllRedDenseTable(new BoundingBox(1, 80.0, 1384.0, 500.0, 1438.0)));

        List<IObject> filtered = UnderlineFootnoteTableFilterProcessor.filterFootnoteTables(contents, pageBox);

        Assertions.assertEquals(1, filtered.size());
        Assertions.assertTrue(filtered.get(0) instanceof TableBorder);
    }

    @Test
    void keepsTable_atBottom_allRedButNotInTop5Percent() {
        // Table with all red text but positioned below the top 5%
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 1224.0, 1512.0);
        List<IObject> contents = new ArrayList<>();

        // bottomY=800, topY=860: both well below the top 5% threshold (1436.4)
        contents.add(createFootnoteTable(new BoundingBox(1, 80.0, 800.0, 500.0, 860.0)));

        List<IObject> filtered = UnderlineFootnoteTableFilterProcessor.filterFootnoteTables(contents, pageBox);

        Assertions.assertEquals(1, filtered.size());
        Assertions.assertTrue(filtered.get(0) instanceof TableBorder);
    }

    // --- Helper methods ---

    private static SemanticTextNode createRedTextNode(BoundingBox box, String text) {
        TextChunk chunk = new TextChunk(box, text, 10, box.getBottomY());
        chunk.setFontColor(new double[]{1.0, 0.0, 0.0});
        return new SemanticTextNode(chunk);
    }

    private static SemanticTextNode createBlackTextNode(BoundingBox box, String text) {
        TextChunk chunk = new TextChunk(box, text, 10, box.getBottomY());
        chunk.setFontColor(new double[]{0.0, 0.0, 0.0});
        return new SemanticTextNode(chunk);
    }

    /**
     * Creates a footnote table with specific text in the first column of each row.
     * Simulates the input3.pdf pattern where footnote descriptions are in column 0.
     */
    private static TableBorder createFootnoteTableWithText(BoundingBox box, String... texts) {
        int numRows = texts.length;
        int numCols = 12;
        TableBorder table = new TableBorder(numRows, numCols);
        double rowHeight = (box.getTopY() - box.getBottomY()) / numRows;
        double colWidth = (box.getRightX() - box.getLeftX()) / numCols;

        for (int row = 0; row < numRows; row++) {
            TableBorderRow tableRow = new TableBorderRow(row, numCols, null);
            for (int col = 0; col < numCols; col++) {
                TableBorderCell cell = new TableBorderCell(row, col, 1, 1, null);
                BoundingBox cellBox = new BoundingBox(1,
                        box.getLeftX() + colWidth * col,
                        box.getTopY() - rowHeight * (row + 1),
                        box.getLeftX() + colWidth * (col + 1),
                        box.getTopY() - rowHeight * row);
                cell.setBoundingBox(cellBox);

                if (col == 0 && row < texts.length) {
                    // First column has the footnote description text
                    cell.addContentObject(createRedTextNode(cellBox, texts[row]));
                } else if ((row * numCols + col) % 3 == 0) {
                    // ~33% empty cells to satisfy the empty ratio condition
                    // cell stays empty
                } else {
                    // Other cells have filler text
                    cell.addContentObject(createRedTextNode(cellBox, "filler"));
                }

                tableRow.getCells()[col] = cell;
            }
            tableRow.setBoundingBox(new BoundingBox(1,
                    box.getLeftX(),
                    box.getTopY() - rowHeight * (row + 1),
                    box.getRightX(),
                    box.getTopY() - rowHeight * row));
            table.getRows()[row] = tableRow;
        }
        table.setBoundingBox(box);
        return table;
    }

    /**
     * Creates a footnote-style table matching the input3.pdf pattern:
     * 4 rows x 12 cols, all text red, ~29% empty cells, positioned at page bottom.
     */
    private static TableBorder createFootnoteTable(BoundingBox box) {
        TableBorder table = new TableBorder(4, 12);
        double rowHeight = (box.getTopY() - box.getBottomY()) / 4.0;
        double colWidth = (box.getRightX() - box.getLeftX()) / 12.0;

        for (int row = 0; row < 4; row++) {
            TableBorderRow tableRow = new TableBorderRow(row, 12, null);
            for (int col = 0; col < 12; col++) {
                TableBorderCell cell = new TableBorderCell(row, col, 1, 1, null);
                BoundingBox cellBox = new BoundingBox(1,
                        box.getLeftX() + colWidth * col,
                        box.getTopY() - rowHeight * (row + 1),
                        box.getLeftX() + colWidth * (col + 1),
                        box.getTopY() - rowHeight * row);
                cell.setBoundingBox(cellBox);

                // ~29% empty cells (14 of 48 for input3 pattern)
                boolean hasContent = (row * 12 + col) % 7 != 0 && (row * 12 + col) % 5 != 0;
                if (hasContent) {
                    cell.addContentObject(createRedTextNode(cellBox, "text" + row + col));
                }

                tableRow.getCells()[col] = cell;
            }
            tableRow.setBoundingBox(new BoundingBox(1,
                    box.getLeftX(),
                    box.getTopY() - rowHeight * (row + 1),
                    box.getRightX(),
                    box.getTopY() - rowHeight * row));
            table.getRows()[row] = tableRow;
        }
        table.setBoundingBox(box);
        return table;
    }

    /**
     * Creates a table with mixed text colors (black + some red for negative values).
     */
    private static TableBorder createMixedColorTable(BoundingBox box) {
        TableBorder table = new TableBorder(3, 3);
        double rowHeight = (box.getTopY() - box.getBottomY()) / 3.0;
        double colWidth = (box.getRightX() - box.getLeftX()) / 3.0;

        for (int row = 0; row < 3; row++) {
            TableBorderRow tableRow = new TableBorderRow(row, 3, null);
            for (int col = 0; col < 3; col++) {
                TableBorderCell cell = new TableBorderCell(row, col, 1, 1, null);
                BoundingBox cellBox = new BoundingBox(1,
                        box.getLeftX() + colWidth * col,
                        box.getTopY() - rowHeight * (row + 1),
                        box.getLeftX() + colWidth * (col + 1),
                        box.getTopY() - rowHeight * row);
                cell.setBoundingBox(cellBox);

                // Row 1 has one red cell (negative value), others black
                SemanticTextNode textNode = (row == 1 && col == 2)
                        ? createRedTextNode(cellBox, "R" + row + "C" + col)
                        : createBlackTextNode(cellBox, "R" + row + "C" + col);
                cell.addContentObject(textNode);

                tableRow.getCells()[col] = cell;
            }
            tableRow.setBoundingBox(new BoundingBox(1,
                    box.getLeftX(),
                    box.getTopY() - rowHeight * (row + 1),
                    box.getRightX(),
                    box.getTopY() - rowHeight * row));
            table.getRows()[row] = tableRow;
        }
        table.setBoundingBox(box);
        return table;
    }

    /**
     * Creates a dense table at the bottom with all cells filled (0% empty, black text).
     */
    private static TableBorder createDenseTableAtTop5Percent(BoundingBox box) {
        TableBorder table = new TableBorder(2, 4);
        double rowHeight = (box.getTopY() - box.getBottomY()) / 2.0;
        double colWidth = (box.getRightX() - box.getLeftX()) / 4.0;

        for (int row = 0; row < 2; row++) {
            TableBorderRow tableRow = new TableBorderRow(row, 4, null);
            for (int col = 0; col < 4; col++) {
                TableBorderCell cell = new TableBorderCell(row, col, 1, 1, null);
                BoundingBox cellBox = new BoundingBox(1,
                        box.getLeftX() + colWidth * col,
                        box.getTopY() - rowHeight * (row + 1),
                        box.getLeftX() + colWidth * (col + 1),
                        box.getTopY() - rowHeight * row);
                cell.setBoundingBox(cellBox);

                SemanticParagraph paragraph = new SemanticParagraph();
                paragraph.add(new TextLine(new TextChunk(cellBox, "data", 10, cellBox.getBottomY())));
                cell.addContentObject(paragraph);

                tableRow.getCells()[col] = cell;
            }
            tableRow.setBoundingBox(new BoundingBox(1,
                    box.getLeftX(),
                    box.getTopY() - rowHeight * (row + 1),
                    box.getRightX(),
                    box.getTopY() - rowHeight * row));
            table.getRows()[row] = tableRow;
        }
        table.setBoundingBox(box);
        return table;
    }

    /**
     * Creates a dense table at the bottom with all red text but 0% empty cells.
     */
    private static TableBorder createAllRedDenseTable(BoundingBox box) {
        TableBorder table = new TableBorder(2, 4);
        double rowHeight = (box.getTopY() - box.getBottomY()) / 2.0;
        double colWidth = (box.getRightX() - box.getLeftX()) / 4.0;

        for (int row = 0; row < 2; row++) {
            TableBorderRow tableRow = new TableBorderRow(row, 4, null);
            for (int col = 0; col < 4; col++) {
                TableBorderCell cell = new TableBorderCell(row, col, 1, 1, null);
                BoundingBox cellBox = new BoundingBox(1,
                        box.getLeftX() + colWidth * col,
                        box.getTopY() - rowHeight * (row + 1),
                        box.getLeftX() + colWidth * (col + 1),
                        box.getTopY() - rowHeight * row);
                cell.setBoundingBox(cellBox);

                cell.addContentObject(createRedTextNode(cellBox, "data"));

                tableRow.getCells()[col] = cell;
            }
            tableRow.setBoundingBox(new BoundingBox(1,
                    box.getLeftX(),
                    box.getTopY() - rowHeight * (row + 1),
                    box.getRightX(),
                    box.getTopY() - rowHeight * row));
            table.getRows()[row] = tableRow;
        }
        table.setBoundingBox(box);
        return table;
    }
}
