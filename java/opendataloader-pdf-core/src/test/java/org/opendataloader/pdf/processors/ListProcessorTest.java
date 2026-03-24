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
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.maps.AccumulatedNodeMapper;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

public class ListProcessorTest {

    @Test
    public void testProcessLists() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        List<IObject> pageContents = new ArrayList<>();
        List<List<IObject>> contents = new ArrayList<>();
        contents.add(pageContents);
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "1. test", 10, 30.0)));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "2. test", 10, 20.0)));
        ListProcessor.processLists(contents, false);
        Assertions.assertEquals(1, contents.get(0).size());
        Assertions.assertTrue(contents.get(0).get(0) instanceof PDFList);
    }

    @Test
    public void testProcessListsFromTextNodes() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticContainers.setAccumulatedNodeMapper(new AccumulatedNodeMapper());
        List<IObject> contents = new ArrayList<>();
        SemanticParagraph paragraph1 = new SemanticParagraph();
        contents.add(paragraph1);
        paragraph1.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "1. test", 10, 30.0)));
        SemanticParagraph paragraph2 = new SemanticParagraph();
        contents.add(paragraph2);
        paragraph2.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "2. test", 10, 20.0)));
        contents = ListProcessor.processListsFromTextNodes(contents);
        Assertions.assertEquals(1, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof PDFList);
    }

    @Test
    public void testCheckNeighborLists() {
        StaticContainers.setIsDataLoader(true);
        List<IObject> pageContents = new ArrayList<>();
        List<List<IObject>> contents = new ArrayList<>();
        contents.add(pageContents);
        PDFList list1 = new PDFList();
        PDFList list2 = new PDFList();
        pageContents.add(list1);
        pageContents.add(list2);
        ListItem listItem1 = new ListItem(new BoundingBox(), 1l);
        listItem1.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 50.0, 20.0, 60.0),
            "1. test", 10, 50.0)));
        list1.add(listItem1);
        ListItem listItem2 = new ListItem(new BoundingBox(), 2l);
        listItem2.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 40.0, 20.0, 50.0),
            "2. test", 10, 40.0)));
        list1.add(listItem2);
        ListItem listItem3 = new ListItem(new BoundingBox(), 3l);
        listItem3.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "3. test", 10, 30.0)));
        list2.add(listItem3);
        ListItem listItem4 = new ListItem(new BoundingBox(), 4l);
        listItem4.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "4. test", 10, 20.0)));
        list2.add(listItem4);
        ListProcessor.checkNeighborLists(contents);
        contents.set(0, DocumentProcessor.removeNullObjectsFromList(contents.get(0)));
        Assertions.assertEquals(1, contents.size());
        Assertions.assertEquals(1, contents.get(0).size());
        Assertions.assertTrue(contents.get(0).get(0) instanceof PDFList);
        Assertions.assertEquals(4, ((PDFList) contents.get(0).get(0)).getNumberOfListItems());
    }

    @Test
    public void testProcessListsFromTextNodesSkipsSectionHeadingSequence() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticContainers.setAccumulatedNodeMapper(new AccumulatedNodeMapper());
        StaticLayoutContainers.setCurrentContentId(1);

        List<IObject> contents = new ArrayList<>();
        contents.add(createParagraph("2. 생산현황(공정별)", 50.0, 120.0, 250.0, 130.0));
        contents.add(createTable(48.0, 90.0, 400.0, 115.0));
        contents.add(createParagraph("3. 생산현황(모델별)", 50.0, 70.0, 250.0, 80.0));
        contents.add(createTable(48.0, 40.0, 400.0, 65.0));

        contents = ListProcessor.processListsFromTextNodes(contents);

        Assertions.assertEquals(4, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof SemanticParagraph);
        Assertions.assertTrue(contents.get(1) instanceof TableBorder);
        Assertions.assertTrue(contents.get(2) instanceof SemanticParagraph);
        Assertions.assertTrue(contents.get(3) instanceof TableBorder);
    }

    @Test
    public void testProcessListsSkipsSectionHeadingSequenceWithCompoundLastHeading() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);

        List<IObject> pageContents = new ArrayList<>();
        List<List<IObject>> contents = new ArrayList<>();
        contents.add(pageContents);

        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 50.0, 180.0, 250.0, 190.0),
            "1. 매출 / 매입 실적 현황", 10, 180.0)));
        pageContents.add(createTable(48.0, 150.0, 400.0, 175.0));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 50.0, 120.0, 250.0, 130.0),
            "2. 생산현황(공정별)", 10, 120.0)));
        pageContents.add(createTable(48.0, 90.0, 400.0, 115.0));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 50.0, 60.0, 320.0, 70.0),
            "6. 장기재고현황 7. 적기처리율", 10, 60.0)));
        pageContents.add(createTable(48.0, 30.0, 400.0, 55.0));

        ListProcessor.processLists(contents, false);

        Assertions.assertEquals(6, contents.get(0).size());
        Assertions.assertTrue(contents.get(0).get(0) instanceof TextLine);
        Assertions.assertTrue(contents.get(0).get(1) instanceof TableBorder);
        Assertions.assertTrue(contents.get(0).get(2) instanceof TextLine);
        Assertions.assertTrue(contents.get(0).get(3) instanceof TableBorder);
        Assertions.assertTrue(contents.get(0).get(4) instanceof TextLine);
        Assertions.assertTrue(contents.get(0).get(5) instanceof TableBorder);
    }

    @Test
    public void testProcessListsWithSingleCharacterLabels() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        List<IObject> pageContents = new ArrayList<>();
        List<List<IObject>> contents = new ArrayList<>();
        contents.add(pageContents);
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 50.0, 20.0, 60.0),
            "1", 10, 50.0)));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 40.0, 20.0, 50.0),
            "가. 첫 번째 항목", 10, 40.0)));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "나. 두 번째 항목", 10, 30.0)));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            ")", 10, 20.0)));
        int originalSize = pageContents.size();
        ListProcessor.processLists(contents, false);
        Assertions.assertFalse(contents.get(0).isEmpty(),
            "Content should not be empty after processing");
        Assertions.assertTrue(contents.get(0).size() <= originalSize,
            "Content size should not exceed original size");
    }

    @Test
    public void testProcessListsWithEdgeCaseLabels() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        List<IObject> pageContents = new ArrayList<>();
        List<List<IObject>> contents = new ArrayList<>();
        contents.add(pageContents);
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 50.0, 20.0, 60.0),
            "a", 10, 50.0)));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 40.0, 20.0, 50.0),
            "b", 10, 40.0)));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "1)", 10, 30.0)));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "2)", 10, 20.0)));
        int originalSize = pageContents.size();
        ListProcessor.processLists(contents, false);
        Assertions.assertFalse(contents.get(0).isEmpty(),
            "Content should not be empty after processing");
        Assertions.assertTrue(contents.get(0).size() <= originalSize,
            "Content size should not exceed original size");
    }

    private static SemanticParagraph createParagraph(String value, double leftX, double bottomY, double rightX, double topY) {
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(new TextChunk(new BoundingBox(0, leftX, bottomY, rightX, topY),
            value, 10, bottomY)));
        return paragraph;
    }

    private static TableBorder createTable(double leftX, double bottomY, double rightX, double topY) {
        TableBorder table = new TableBorder(1, 1);
        table.setBoundingBox(new BoundingBox(0, leftX, bottomY, rightX, topY));
        return table;
    }
}
