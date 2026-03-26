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
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.List;

class DominantImageTextFilterProcessorTest {

    @Test
    void removesTextCoveredByDominantImage() {
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 800.0, 600.0);
        List<IObject> contents = new ArrayList<>();
        contents.add(new ImageChunk(new BoundingBox(1, 60.406, 27.41, 776.806, 567.666)));
        contents.add(createHeading(new BoundingBox(1, 137.31, 503.041, 434.498, 527.626), "2024년 지진발생시 행동요령"));
        contents.add(createParagraph(new BoundingBox(1, 665.943, 468.734, 762.136, 483.16), "국민안전처 자료"));
        contents.add(createParagraph(new BoundingBox(1, 173.452, 26.901, 765.994, 50.533), "템플릿_01 3"));

        List<IObject> filtered = DominantImageTextFilterProcessor.filterCoveredText(contents, pageBox);

        Assertions.assertEquals(1, filtered.size());
        Assertions.assertTrue(filtered.get(0) instanceof ImageChunk);
    }

    @Test
    void keepsTextWhenImageIsNotDominant() {
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 800.0, 600.0);
        List<IObject> contents = new ArrayList<>();
        contents.add(new ImageChunk(new BoundingBox(1, 60.0, 400.0, 180.0, 500.0)));
        contents.add(createHeading(new BoundingBox(1, 200.0, 500.0, 420.0, 530.0), "제목"));

        List<IObject> filtered = DominantImageTextFilterProcessor.filterCoveredText(contents, pageBox);

        Assertions.assertEquals(2, filtered.size());
    }

    private static SemanticHeading createHeading(BoundingBox boundingBox, String text) {
        return new SemanticHeading(createParagraph(boundingBox, text));
    }

    private static SemanticParagraph createParagraph(BoundingBox boundingBox, String text) {
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(new TextChunk(boundingBox, text, 10, 10.0)));
        return paragraph;
    }
}
