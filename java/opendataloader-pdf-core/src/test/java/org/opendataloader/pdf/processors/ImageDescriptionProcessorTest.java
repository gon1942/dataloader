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
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

class ImageDescriptionProcessorTest {

    @Test
    void skipsSmallTopLogoImage() {
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 800.0, 600.0);
        ImageChunk logo = new ImageChunk(new BoundingBox(1, 20.0, 560.0, 160.0, 595.0));

        Assertions.assertFalse(ImageDescriptionProcessor.shouldDescribeImage(logo, pageBox));
    }

    @Test
    void skipsSmallBottomLogoImage() {
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 800.0, 600.0);
        ImageChunk logo = new ImageChunk(new BoundingBox(1, 640.0, 5.0, 770.0, 40.0));

        Assertions.assertFalse(ImageDescriptionProcessor.shouldDescribeImage(logo, pageBox));
    }

    @Test
    void keepsMainBodyImageEvenNearTopWhenLarge() {
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 800.0, 600.0);
        ImageChunk largeImage = new ImageChunk(new BoundingBox(1, 80.0, 260.0, 720.0, 590.0));

        Assertions.assertTrue(ImageDescriptionProcessor.shouldDescribeImage(largeImage, pageBox));
    }

    @Test
    void keepsBodyImageInMiddleOfPage() {
        BoundingBox pageBox = new BoundingBox(1, 0.0, 0.0, 800.0, 600.0);
        ImageChunk bodyImage = new ImageChunk(new BoundingBox(1, 150.0, 180.0, 650.0, 420.0));

        Assertions.assertTrue(ImageDescriptionProcessor.shouldDescribeImage(bodyImage, pageBox));
    }
}
