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
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

public class SectionHeadingProcessorTest {

    @Test
    public void splitCompoundSectionHeadings_splitsWideSeparatedNumberedHeadings() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        List<IObject> contents = new ArrayList<>();
        contents.add(createParagraph("6. 장기재고현황         7. 적기처리율", 50.0, 10.0, 450.0, 25.0));

        List<IObject> result = SectionHeadingProcessor.splitCompoundSectionHeadings(contents);

        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals("6. 장기재고현황", ((SemanticParagraph) result.get(0)).getValue());
        Assertions.assertEquals("7. 적기처리율", ((SemanticParagraph) result.get(1)).getValue());
    }

    @Test
    public void splitCompoundSectionHeadings_keepsSingleHeadingUntouched() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        List<IObject> contents = new ArrayList<>();
        contents.add(createParagraph("6. 장기재고현황", 50.0, 10.0, 180.0, 25.0));

        List<IObject> result = SectionHeadingProcessor.splitCompoundSectionHeadings(contents);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals("6. 장기재고현황", ((SemanticParagraph) result.get(0)).getValue());
    }

    private static SemanticParagraph createParagraph(String value, double leftX, double bottomY, double rightX, double topY) {
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(new TextChunk(new BoundingBox(0, leftX, bottomY, rightX, topY),
            value, "Font1", 12, 700, 0, bottomY, new double[]{0.0}, null, 0)));
        return paragraph;
    }
}
