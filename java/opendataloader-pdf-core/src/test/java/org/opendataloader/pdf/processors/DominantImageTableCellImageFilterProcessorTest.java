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

class DominantImageTableCellImageFilterProcessorTest {

    @Test
    void removesNestedCellImagesWhenDominantImageExists() {
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 780.0, 540.0);
        List<IObject> contents = new ArrayList<>();
        contents.add(new ImageChunk(new BoundingBox(1, 0.0, -35.943, 959.953, 540.028)));

        TableBorder table = new TableBorder(2, 2);
        table.setBoundingBox(new BoundingBox(1, 65.0, 33.0, 707.0, 392.0));
        TableBorderRow header = new TableBorderRow(0, 2, null);
        TableBorderRow body = new TableBorderRow(1, 2, null);

        TableBorderCell headerCell = new TableBorderCell(0, 0, 1, 1, null);
        headerCell.setBoundingBox(new BoundingBox(1, 65.0, 340.0, 180.0, 392.0));
        headerCell.addContentObject(createParagraph(headerCell.getBoundingBox(), "순번"));
        header.getCells()[0] = headerCell;
        header.getCells()[1] = headerCell;
        header.setBoundingBox(new BoundingBox(1, 65.0, 340.0, 707.0, 392.0));

        TableBorderCell bodyCell = new TableBorderCell(1, 0, 1, 1, null);
        bodyCell.setBoundingBox(new BoundingBox(1, 126.0, 253.0, 248.0, 351.0));
        bodyCell.addContentObject(new ImageChunk(new BoundingBox(1, 0.0, 156.0, 191.0, 348.0)));
        bodyCell.addContentObject(new ImageChunk(new BoundingBox(1, 191.0, 156.0, 383.0, 348.0)));
        bodyCell.addContentObject(createParagraph(bodyCell.getBoundingBox(), "DCS Panel"));
        body.getCells()[0] = bodyCell;

        TableBorderCell bodyCell2 = new TableBorderCell(1, 1, 1, 1, null);
        bodyCell2.setBoundingBox(new BoundingBox(1, 248.0, 253.0, 570.0, 351.0));
        bodyCell2.addContentObject(createParagraph(bodyCell2.getBoundingBox(), "Control Panel"));
        body.getCells()[1] = bodyCell2;
        body.setBoundingBox(new BoundingBox(1, 126.0, 253.0, 570.0, 351.0));

        table.getRows()[0] = header;
        table.getRows()[1] = body;
        contents.add(table);

        DominantImageTableCellImageFilterProcessor.filterNestedTableImages(contents, pageBox);

        Assertions.assertEquals(1, bodyCell.getContents().size());
        Assertions.assertTrue(bodyCell.getContents().get(0) instanceof SemanticParagraph);
    }

    @Test
    void removesTinyImageOnlyCellContentWhenDominantImageExists() {
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 780.0, 540.0);
        List<IObject> contents = new ArrayList<>();
        contents.add(new ImageChunk(new BoundingBox(1, 0.0, -35.943, 959.953, 540.028)));

        TableBorder table = new TableBorder(1, 1);
        table.setBoundingBox(new BoundingBox(1, 639.0, 33.0, 706.0, 119.0));
        TableBorderRow row = new TableBorderRow(0, 1, null);
        TableBorderCell cell = new TableBorderCell(0, 0, 1, 1, null);
        cell.setBoundingBox(new BoundingBox(1, 639.0, 33.0, 706.0, 119.0));
        cell.addContentObject(new ImageChunk(new BoundingBox(1, 694.8, 48.0, 700.8, 54.0)));
        cell.addContentObject(new ImageChunk(new BoundingBox(1, 700.8, 48.0, 706.8, 54.0)));
        row.getCells()[0] = cell;
        row.setBoundingBox(cell.getBoundingBox());
        table.getRows()[0] = row;
        contents.add(table);

        DominantImageTableCellImageFilterProcessor.filterNestedTableImages(contents, pageBox);

        Assertions.assertTrue(cell.getContents().isEmpty());
    }

    @Test
    void keepsCellImagesWithoutDominantImage() {
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 780.0, 540.0);
        List<IObject> contents = new ArrayList<>();
        contents.add(new ImageChunk(new BoundingBox(1, 10.0, 10.0, 120.0, 120.0)));

        TableBorder table = new TableBorder(1, 1);
        table.setBoundingBox(new BoundingBox(1, 200.0, 200.0, 300.0, 300.0));
        TableBorderRow row = new TableBorderRow(0, 1, null);
        TableBorderCell cell = new TableBorderCell(0, 0, 1, 1, null);
        cell.setBoundingBox(new BoundingBox(1, 200.0, 200.0, 300.0, 300.0));
        cell.addContentObject(new ImageChunk(new BoundingBox(1, 205.0, 205.0, 295.0, 295.0)));
        row.getCells()[0] = cell;
        row.setBoundingBox(cell.getBoundingBox());
        table.getRows()[0] = row;
        contents.add(table);

        DominantImageTableCellImageFilterProcessor.filterNestedTableImages(contents, pageBox);

        Assertions.assertEquals(1, cell.getContents().size());
        Assertions.assertTrue(cell.getContents().get(0) instanceof ImageChunk);
    }

    private static SemanticParagraph createParagraph(BoundingBox boundingBox, String text) {
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(new TextChunk(boundingBox, text, 10, boundingBox.getBottomY())));
        return paragraph;
    }
}
