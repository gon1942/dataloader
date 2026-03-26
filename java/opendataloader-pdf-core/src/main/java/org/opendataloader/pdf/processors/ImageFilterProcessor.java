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

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes decorative or fragment image chunks when a page already contains
 * larger figure-like images. This is primarily intended to suppress PDF image
 * XObjects that represent partial fragments of a larger visual card.
 */
public final class ImageFilterProcessor {
    private static final double CONTAINMENT_PADDING = 32.0;
    private static final double MIN_LARGE_IMAGE_AREA = 25000.0;
    private static final int MIN_LARGE_IMAGE_COUNT_FOR_FILTERING = 2;
    private static final double MAX_SMALL_STANDALONE_AREA = 12000.0;
    private static final double MIN_AREA_RATIO_FOR_CONTAINER = 1.5;

    private ImageFilterProcessor() {
    }

    public static List<IObject> filterImages(List<IObject> contents) {
        List<ImageChunk> images = new ArrayList<>();
        for (IObject content : contents) {
            if (content instanceof ImageChunk) {
                images.add((ImageChunk) content);
            }
        }

        if (images.isEmpty()) {
            return contents;
        }

        int largeImageCount = 0;
        for (ImageChunk image : images) {
            if (getArea(image.getBoundingBox()) >= MIN_LARGE_IMAGE_AREA) {
                largeImageCount++;
            }
        }

        List<IObject> filtered = new ArrayList<>();
        for (IObject content : contents) {
            if (!(content instanceof ImageChunk)) {
                filtered.add(content);
                continue;
            }
            ImageChunk image = (ImageChunk) content;
            if (shouldKeep(image, images, largeImageCount)) {
                filtered.add(content);
            }
        }
        return filtered;
    }

    private static boolean shouldKeep(ImageChunk image, List<ImageChunk> images, int largeImageCount) {
        BoundingBox candidateBox = image.getBoundingBox();
        double candidateArea = getArea(candidateBox);

        for (ImageChunk other : images) {
            if (other == image) {
                continue;
            }
            BoundingBox otherBox = other.getBoundingBox();
            double otherArea = getArea(otherBox);
            if (otherArea < candidateArea * MIN_AREA_RATIO_FOR_CONTAINER) {
                continue;
            }
            if (containsWithPadding(otherBox, candidateBox, CONTAINMENT_PADDING)) {
                return false;
            }
        }

        return largeImageCount < MIN_LARGE_IMAGE_COUNT_FOR_FILTERING || candidateArea > MAX_SMALL_STANDALONE_AREA;
    }

    private static boolean containsWithPadding(BoundingBox outer, BoundingBox inner, double padding) {
        if (outer == null || inner == null) {
            return false;
        }
        if (!outer.getPageNumber().equals(inner.getPageNumber())) {
            return false;
        }
        BoundingBox expanded = new BoundingBox(outer);
        expanded.setLeftX(expanded.getLeftX() - padding);
        expanded.setRightX(expanded.getRightX() + padding);
        expanded.setBottomY(expanded.getBottomY() - padding);
        expanded.setTopY(expanded.getTopY() + padding);
        return expanded.contains(inner);
    }

    private static double getArea(BoundingBox box) {
        if (box == null) {
            return 0.0;
        }
        return Math.max(0.0, box.getRightX() - box.getLeftX()) * Math.max(0.0, box.getTopY() - box.getBottomY());
    }
}
