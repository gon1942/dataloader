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
            expanded.setLeftX(Math.max(expanded.getLeftX(), pageBoundingBox.getLeftX()));
            expanded.setRightX(Math.min(expanded.getRightX(), pageBoundingBox.getRightX()));
            expanded.setBottomY(Math.max(expanded.getBottomY(), pageBoundingBox.getBottomY()));
            expanded.setTopY(Math.min(expanded.getTopY(), pageBoundingBox.getTopY()));
        }
        return expanded;
    }
}
