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

class ImageFilterProcessorTest {

    @Test
    void filterSmallFragmentsWhenLargeCardsExist() {
        List<IObject> contents = new ArrayList<>();
        contents.add(new ImageChunk(new BoundingBox(1, 64.006, 264.528, 292.053, 457.085)));
        contents.add(new ImageChunk(new BoundingBox(1, 303.279, 264.528, 534.813, 457.085)));
        contents.add(new ImageChunk(new BoundingBox(1, 545.641, 264.528, 776.806, 457.085)));
        contents.add(new ImageChunk(new BoundingBox(1, 64.006, 55.615, 294.689, 248.286)));
        contents.add(new ImageChunk(new BoundingBox(1, 305.065, 55.615, 535.267, 248.286)));
        contents.add(new ImageChunk(new BoundingBox(1, 545.641, 55.615, 776.806, 248.286)));
        contents.add(new ImageChunk(new BoundingBox(1, 60.406, 447.08, 127.474, 567.666)));
        contents.add(new ImageChunk(new BoundingBox(1, 60.406, 274.365, 124.469, 437.3)));
        contents.add(new ImageChunk(new BoundingBox(1, 151.285, 244.261, 284.4, 406.828)));
        contents.add(new ImageChunk(new BoundingBox(1, 60.406, 27.41, 165.118, 109.275)));
        contents.add(new SemanticParagraph());

        List<IObject> filtered = ImageFilterProcessor.filterImages(contents);

        long imageCount = filtered.stream().filter(ImageChunk.class::isInstance).count();
        Assertions.assertEquals(6, imageCount);
        Assertions.assertEquals(7, filtered.size());
        Assertions.assertTrue(filtered.get(filtered.size() - 1) instanceof SemanticParagraph);
    }

    @Test
    void keepSingleSmallImageWhenNoLargeCardsExist() {
        List<IObject> contents = new ArrayList<>();
        ImageChunk smallImage = new ImageChunk(new BoundingBox(1, 10.0, 10.0, 40.0, 50.0));
        contents.add(smallImage);

        List<IObject> filtered = ImageFilterProcessor.filterImages(contents);

        Assertions.assertEquals(1, filtered.size());
        Assertions.assertSame(smallImage, filtered.get(0));
    }
}
