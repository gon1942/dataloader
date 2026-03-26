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
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.List;

class ImageMergeProcessorTest {

    @Test
    void mergeContainedImages() {
        List<IObject> contents = new ArrayList<>();
        ImageChunk outer = new ImageChunk(new BoundingBox(1, 60.406, 447.08, 127.474, 567.666));
        outer.setRecognizedStructureId(10L);
        ImageChunk inner = new ImageChunk(new BoundingBox(1, 108.709, 501.42, 114.52, 520.667));
        inner.setRecognizedStructureId(11L);
        SemanticParagraph paragraph = new SemanticParagraph();

        contents.add(outer);
        contents.add(paragraph);
        contents.add(inner);

        List<IObject> merged = ImageMergeProcessor.mergeImages(contents);

        Assertions.assertEquals(2, merged.size());
        Assertions.assertTrue(merged.get(0) instanceof ImageChunk);
        Assertions.assertTrue(merged.get(1) instanceof SemanticParagraph);

        ImageChunk mergedImage = (ImageChunk) merged.get(0);
        Assertions.assertEquals(60.406, mergedImage.getBoundingBox().getLeftX(), 0.001);
        Assertions.assertEquals(447.08, mergedImage.getBoundingBox().getBottomY(), 0.001);
        Assertions.assertEquals(127.474, mergedImage.getBoundingBox().getRightX(), 0.001);
        Assertions.assertEquals(567.666, mergedImage.getBoundingBox().getTopY(), 0.001);
        Assertions.assertEquals(10L, mergedImage.getRecognizedStructureId());
    }
}
