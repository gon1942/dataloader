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
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.ArrayList;
import java.util.List;

class DominantImageTableFilterProcessorTest {

    @Test
    void removesSparseSmallTablesInsideDominantImage() {
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 960.0, 540.0);
        List<IObject> contents = new ArrayList<>();
        contents.add(new ImageChunk(new BoundingBox(1, 0.0, 0.057, 959.981, 540.029)));
        contents.add(createTwoByTwoLabeledBox(new BoundingBox(1, 634.308, 381.005, 701.774, 437.755), "Control Board Demux."));
        contents.add(new SemanticParagraph());

        List<IObject> filtered = DominantImageTableFilterProcessor.filterFalseTables(contents, pageBox);

        Assertions.assertEquals(2, filtered.size());
        Assertions.assertTrue(filtered.get(0) instanceof ImageChunk);
        Assertions.assertTrue(filtered.get(1) instanceof SemanticParagraph);
    }

    @Test
    void keepsRegularTableWithoutDominantImage() {
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 960.0, 540.0);
        List<IObject> contents = new ArrayList<>();
        contents.add(new ImageChunk(new BoundingBox(1, 20.0, 20.0, 120.0, 120.0)));
        contents.add(createDataTable(new BoundingBox(1, 200.0, 120.0, 700.0, 360.0)));

        List<IObject> filtered = DominantImageTableFilterProcessor.filterFalseTables(contents, pageBox);

        Assertions.assertEquals(2, filtered.size());
        Assertions.assertTrue(filtered.get(1) instanceof TableBorder);
    }

    private static TableBorder createTwoByTwoLabeledBox(BoundingBox box, String label) {
        TableBorder table = new TableBorder(2, 2);

        TableBorderRow row0 = new TableBorderRow(0, 2, null);
        TableBorderCell merged = new TableBorderCell(0, 0, 1, 2, null);
        merged.setBoundingBox(new BoundingBox(1, box.getLeftX(), (box.getBottomY() + box.getTopY()) / 2.0, box.getRightX(), box.getTopY()));
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(new TextChunk(merged.getBoundingBox(), label, 10, merged.getBoundingBox().getBottomY())));
        merged.addContentObject(paragraph);
        row0.getCells()[0] = merged;
        row0.getCells()[1] = merged;
        row0.setBoundingBox(merged.getBoundingBox());

        TableBorderRow row1 = new TableBorderRow(1, 2, null);
        TableBorderCell empty1 = new TableBorderCell(1, 0, 1, 1, null);
        empty1.setBoundingBox(new BoundingBox(1, box.getLeftX(), box.getBottomY(), (box.getLeftX() + box.getRightX()) / 2.0, (box.getBottomY() + box.getTopY()) / 2.0));
        TableBorderCell empty2 = new TableBorderCell(1, 1, 1, 1, null);
        empty2.setBoundingBox(new BoundingBox(1, (box.getLeftX() + box.getRightX()) / 2.0, box.getBottomY(), box.getRightX(), (box.getBottomY() + box.getTopY()) / 2.0));
        row1.getCells()[0] = empty1;
        row1.getCells()[1] = empty2;
        row1.setBoundingBox(new BoundingBox(1, box.getLeftX(), box.getBottomY(), box.getRightX(), (box.getBottomY() + box.getTopY()) / 2.0));

        table.getRows()[0] = row0;
        table.getRows()[1] = row1;
        table.setBoundingBox(box);
        return table;
    }

    private static TableBorder createDataTable(BoundingBox box) {
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
                SemanticParagraph paragraph = new SemanticParagraph();
                paragraph.add(new TextLine(new TextChunk(cellBox, "R" + row + "C" + col, 10, cellBox.getBottomY())));
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
}
