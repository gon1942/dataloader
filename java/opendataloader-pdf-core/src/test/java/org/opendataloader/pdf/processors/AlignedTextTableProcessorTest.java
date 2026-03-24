/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.util.ArrayList;
import java.util.List;

class AlignedTextTableProcessorTest {

    @Test
    void detectsAlignedTextGridWhenNoBorderTableExists() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setCurrentContentId(1L);

        List<IObject> contents = new ArrayList<>();
        addRow(contents, 0, 10, 90, "항목", "실적", "비율", "예산");
        addRow(contents, 0, 10, 75, "매출", "100", "50%", "90");
        addRow(contents, 0, 10, 60, "원가", "70", "35%", "60");
        addRow(contents, 0, 10, 45, "판관비", "10", "5%", "12");
        addRow(contents, 0, 10, 30, "영업이익", "20", "10%", "18");
        addRow(contents, 0, 10, 15, "경상이익", "18", "9%", "16");

        for (int row = 0; row < 6; row++) {
            double top = 90 - row * 15;
            contents.add(new LineArtChunk(new BoundingBox(0, 5.0, top - 10.0, 350.0, top + 2.0)));
            contents.add(new LineArtChunk(new BoundingBox(0, 5.0, top - 12.0, 25.0, top + 2.0)));
        }

        List<IObject> result = AlignedTextTableProcessor.detectAlignedTextTables(contents);

        Assertions.assertEquals(1, result.stream().filter(TableBorder.class::isInstance).count());
        TableBorder table = (TableBorder) result.stream().filter(TableBorder.class::isInstance).findFirst().orElseThrow();
        Assertions.assertEquals(6, table.getNumberOfRows());
        Assertions.assertEquals(4, table.getNumberOfColumns());
        Assertions.assertTrue(table.getCell(0, 0).getContents().get(0) instanceof SemanticParagraph);
        Assertions.assertEquals("항목",
            ((SemanticParagraph) table.getCell(0, 0).getContents().get(0)).getValue());
    }

    @Test
    void skipsFallbackWhenRealTableAlreadyExists() {
        List<IObject> contents = new ArrayList<>();
        TableBorder existing = new TableBorder(1, 1);
        existing.setBoundingBox(new BoundingBox(0, 0.0, 0.0, 10.0, 10.0));
        contents.add(existing);
        contents.add(new TextLine(createChunk(0, 20.0, 20.0, 30.0, 30.0, "ignored")));

        List<IObject> result = AlignedTextTableProcessor.detectAlignedTextTables(contents);

        Assertions.assertSame(contents, result);
        Assertions.assertSame(existing, result.get(0));
    }

    private static void addRow(List<IObject> contents, int page, double leftX, double topY, String... values) {
        TextLine line = new TextLine();
        double x = leftX;
        for (int index = 0; index < values.length; index++) {
            String value = values[index];
            double width = value.length() * 8.0 + 8.0;
            TextChunk chunk = createChunk(page, x, topY - 10.0, x + width, topY, value);
            line.add(chunk);
            x += index == 0 ? 110.0 : 85.0;
        }
        contents.add(line);
    }

    private static TextChunk createChunk(int page, double leftX, double bottomY, double rightX, double topY, String value) {
        TextChunk chunk = new TextChunk(new BoundingBox(page, leftX, bottomY, rightX, topY), value, 10.0, bottomY);
        chunk.getStreamInfos().add(new StreamInfo(page, null, 0, value.length()));
        chunk.adjustSymbolEndsToBoundingBox(null);
        return chunk;
    }
}
