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
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.ArrayList;
import java.util.List;

class TableHeaderMergeProcessorTest {

    @Test
    void mergesDetachedHeaderRowsIntoTableWithoutUsingContentKeywords() {
        List<IObject> pageContents = new ArrayList<>();
        pageContents.add(createParagraph("Group A", 100.0, 148.0, 220.0, 160.0));
        pageContents.add(createParagraph("Group B", 220.0, 148.0, 340.0, 160.0));
        pageContents.add(createParagraph("Name", 100.0, 132.0, 160.0, 144.0));
        pageContents.add(createParagraph("Actual", 160.0, 132.0, 220.0, 144.0));
        pageContents.add(createParagraph("Budget", 220.0, 132.0, 280.0, 144.0));
        pageContents.add(createParagraph("Ratio", 280.0, 132.0, 340.0, 144.0));
        pageContents.add(createParagraph("Summary 335.4 44.1 213.0 39.7 38.6", 100.0, 116.0, 340.0, 128.0));
        pageContents.add(createTable());

        List<IObject> mergedContents = TableHeaderMergeProcessor.mergeDetachedHeaders(pageContents);

        Assertions.assertEquals(2, mergedContents.size());
        Assertions.assertTrue(mergedContents.get(0) instanceof SemanticParagraph);
        Assertions.assertTrue(mergedContents.get(1) instanceof TableBorder);

        TableBorder mergedTable = (TableBorder) mergedContents.get(1);
        Assertions.assertEquals(4, mergedTable.getNumberOfRows());
        Assertions.assertEquals(4, mergedTable.getNumberOfColumns());
        Assertions.assertEquals("Group A",
            ((SemanticParagraph) mergedTable.getCell(0, 0).getContents().get(0)).getValue());
        Assertions.assertEquals(2, mergedTable.getCell(0, 0).getColSpan());
        Assertions.assertEquals("Ratio",
            ((SemanticParagraph) mergedTable.getCell(1, 3).getContents().get(0)).getValue());
        Assertions.assertEquals("Item 1",
            ((SemanticParagraph) mergedTable.getCell(2, 0).getContents().get(0)).getValue());
    }

    private static SemanticParagraph createParagraph(String value, double leftX, double bottomY, double rightX, double topY) {
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(new TextChunk(new BoundingBox(0, leftX, bottomY, rightX, topY), value, 10.0, bottomY)));
        return paragraph;
    }

    private static TableBorder createTable() {
        TableBorder table = new TableBorder(2, 4);
        table.setRecognizedStructureId(42L);
        table.setBoundingBox(new BoundingBox(0, 100.0, 60.0, 340.0, 112.0));

        TableBorderRow row0 = new TableBorderRow(0, 4, null);
        row0.setBoundingBox(new BoundingBox(0, 100.0, 86.0, 340.0, 112.0));
        row0.getCells()[0] = createCell(0, 0, 100.0, 86.0, 160.0, 112.0, "Item 1");
        row0.getCells()[1] = createCell(0, 1, 160.0, 86.0, 220.0, 112.0, "10");
        row0.getCells()[2] = createCell(0, 2, 220.0, 86.0, 280.0, 112.0, "11");
        row0.getCells()[3] = createCell(0, 3, 280.0, 86.0, 340.0, 112.0, "90%");
        table.getRows()[0] = row0;

        TableBorderRow row1 = new TableBorderRow(1, 4, null);
        row1.setBoundingBox(new BoundingBox(0, 100.0, 60.0, 340.0, 86.0));
        row1.getCells()[0] = createCell(1, 0, 100.0, 60.0, 160.0, 86.0, "Item 2");
        row1.getCells()[1] = createCell(1, 1, 160.0, 60.0, 220.0, 86.0, "12");
        row1.getCells()[2] = createCell(1, 2, 220.0, 60.0, 280.0, 86.0, "10");
        row1.getCells()[3] = createCell(1, 3, 280.0, 60.0, 340.0, 86.0, "120%");
        table.getRows()[1] = row1;
        return table;
    }

    private static TableBorderCell createCell(int row, int col, double leftX, double bottomY,
                                              double rightX, double topY, String value) {
        TableBorderCell cell = new TableBorderCell(row, col, 1, 1, null);
        cell.setBoundingBox(new BoundingBox(0, leftX, bottomY, rightX, topY));
        cell.addContentObject(createParagraph(value, leftX, bottomY, rightX, topY));
        return cell;
    }
}
