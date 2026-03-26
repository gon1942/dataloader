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
package org.opendataloader.pdf.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

class PictureCropUtilsTest {

    @Test
    void usesFullPageCropForNearFullPageOutOfBoundsImage() {
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 779.981, 540.0);
        BoundingBox imageBox = new BoundingBox(1, 0.0, -35.943, 959.953, 540.028);

        Assertions.assertTrue(PictureCropUtils.shouldUseFullPageCrop(imageBox, pageBox));
    }

    @Test
    void keepsPartialImageAsPartialCrop() {
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 779.981, 540.0);
        BoundingBox imageBox = new BoundingBox(1, 120.0, 120.0, 620.0, 420.0);

        Assertions.assertFalse(PictureCropUtils.shouldUseFullPageCrop(imageBox, pageBox));
    }
}
