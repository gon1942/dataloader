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

import org.opendataloader.pdf.processors.DocumentProcessor;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

/**
 * Utility for expanding picture crops so rendered output does not cut off edges.
 */
public final class PictureCropUtils {
    private static final double PADDING_RATIO = 0.05;
    private static final double MIN_PADDING = 6.0;
    private static final double FULL_PAGE_MIN_AREA_RATIO = 0.85;
    private static final double FULL_PAGE_MIN_WIDTH_RATIO = 0.95;
    private static final double FULL_PAGE_MIN_HEIGHT_RATIO = 0.95;
    private static final double MAX_OUT_OF_BOUNDS_RATIO = 0.25;

    private PictureCropUtils() {
    }

    public static BoundingBox getCropBoundingBox(BoundingBox original) {
        if (original == null) {
            return null;
        }

        BoundingBox expanded = new BoundingBox(original);
        double xPadding = Math.max(expanded.getWidth() * PADDING_RATIO, MIN_PADDING);
        double yPadding = Math.max(expanded.getHeight() * PADDING_RATIO, MIN_PADDING);

        expanded.setLeftX(expanded.getLeftX() - xPadding);
        expanded.setRightX(expanded.getRightX() + xPadding);
        expanded.setBottomY(expanded.getBottomY() - yPadding);
        expanded.setTopY(expanded.getTopY() + yPadding);

        BoundingBox pageBoundingBox = DocumentProcessor.getPageBoundingBox(original.getPageNumber());
        if (pageBoundingBox != null) {
            if (shouldUseFullPageCrop(original, pageBoundingBox)) {
                return new BoundingBox(pageBoundingBox);
            }
            clampToPage(expanded, pageBoundingBox);
        }
        return expanded;
    }

    static boolean shouldUseFullPageCrop(BoundingBox original, BoundingBox pageBoundingBox) {
        if (original == null || pageBoundingBox == null) {
            return false;
        }

        double pageWidth = Math.max(0.0, pageBoundingBox.getWidth());
        double pageHeight = Math.max(0.0, pageBoundingBox.getHeight());
        double pageArea = pageWidth * pageHeight;
        if (pageArea <= 0.0) {
            return false;
        }

        double originalWidth = Math.max(0.0, original.getWidth());
        double originalHeight = Math.max(0.0, original.getHeight());
        double originalArea = originalWidth * originalHeight;

        boolean nearFullPage = originalArea >= pageArea * FULL_PAGE_MIN_AREA_RATIO
            && originalWidth >= pageWidth * FULL_PAGE_MIN_WIDTH_RATIO
            && originalHeight >= pageHeight * FULL_PAGE_MIN_HEIGHT_RATIO;
        if (!nearFullPage) {
            return false;
        }

        double outOfBoundsWidth = Math.max(0.0, pageBoundingBox.getLeftX() - original.getLeftX())
            + Math.max(0.0, original.getRightX() - pageBoundingBox.getRightX());
        double outOfBoundsHeight = Math.max(0.0, pageBoundingBox.getBottomY() - original.getBottomY())
            + Math.max(0.0, original.getTopY() - pageBoundingBox.getTopY());

        return outOfBoundsWidth <= pageWidth * MAX_OUT_OF_BOUNDS_RATIO
            && outOfBoundsHeight <= pageHeight * MAX_OUT_OF_BOUNDS_RATIO
            && (outOfBoundsWidth > 0.0 || outOfBoundsHeight > 0.0);
    }

    private static void clampToPage(BoundingBox box, BoundingBox pageBoundingBox) {
        box.setLeftX(Math.max(box.getLeftX(), pageBoundingBox.getLeftX()));
        box.setRightX(Math.min(box.getRightX(), pageBoundingBox.getRightX()));
        box.setBottomY(Math.max(box.getBottomY(), pageBoundingBox.getBottomY()));
        box.setTopY(Math.min(box.getTopY(), pageBoundingBox.getTopY()));
    }
}
