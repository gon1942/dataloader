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
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Suppresses duplicated text content when a single dominant image already
 * represents almost the entire page, such as poster- or flyer-style PDFs.
 */
public final class DominantImageTextFilterProcessor {
    private static final double MIN_PAGE_AREA_RATIO = 0.55;
    private static final double MIN_PAGE_WIDTH_RATIO = 0.75;
    private static final double MIN_PAGE_HEIGHT_RATIO = 0.75;
    private static final double TEXT_CONTAINMENT_PADDING = 8.0;

    private DominantImageTextFilterProcessor() {
    }

    public static List<IObject> filterCoveredText(List<IObject> contents, BoundingBox pageBoundingBox) {
        if (contents == null || contents.isEmpty() || pageBoundingBox == null) {
            return contents;
        }

        ImageChunk dominantImage = findDominantImage(contents, pageBoundingBox);
        if (dominantImage == null) {
            return contents;
        }

        BoundingBox dominantBox = expand(dominantImage.getBoundingBox(), TEXT_CONTAINMENT_PADDING);
        List<IObject> filtered = new ArrayList<>();
        for (IObject content : contents) {
            if (!isCoveredText(content, dominantBox)) {
                filtered.add(content);
            }
        }
        return filtered;
    }

    private static ImageChunk findDominantImage(List<IObject> contents, BoundingBox pageBoundingBox) {
        double pageArea = getArea(pageBoundingBox);
        double pageWidth = Math.max(0.0, pageBoundingBox.getRightX() - pageBoundingBox.getLeftX());
        double pageHeight = Math.max(0.0, pageBoundingBox.getTopY() - pageBoundingBox.getBottomY());
        ImageChunk dominant = null;
        double maxArea = 0.0;

        for (IObject content : contents) {
            if (!(content instanceof ImageChunk)) {
                continue;
            }
            BoundingBox imageBox = content.getBoundingBox();
            double imageArea = getArea(imageBox);
            double imageWidth = Math.max(0.0, imageBox.getRightX() - imageBox.getLeftX());
            double imageHeight = Math.max(0.0, imageBox.getTopY() - imageBox.getBottomY());
            if (imageArea < pageArea * MIN_PAGE_AREA_RATIO) {
                continue;
            }
            if (imageWidth < pageWidth * MIN_PAGE_WIDTH_RATIO || imageHeight < pageHeight * MIN_PAGE_HEIGHT_RATIO) {
                continue;
            }
            if (imageArea > maxArea) {
                dominant = (ImageChunk) content;
                maxArea = imageArea;
            }
        }
        return dominant;
    }

    private static boolean isCoveredText(IObject content, BoundingBox dominantBox) {
        if (!(content instanceof SemanticTextNode) && !(content instanceof SemanticHeaderOrFooter)) {
            return false;
        }
        BoundingBox contentBox = content.getBoundingBox();
        return contentBox != null && dominantBox.contains(contentBox);
    }

    private static BoundingBox expand(BoundingBox box, double padding) {
        BoundingBox expanded = new BoundingBox(box);
        expanded.setLeftX(expanded.getLeftX() - padding);
        expanded.setRightX(expanded.getRightX() + padding);
        expanded.setBottomY(expanded.getBottomY() - padding);
        expanded.setTopY(expanded.getTopY() + padding);
        return expanded;
    }

    private static double getArea(BoundingBox box) {
        if (box == null) {
            return 0.0;
        }
        return Math.max(0.0, box.getRightX() - box.getLeftX()) * Math.max(0.0, box.getTopY() - box.getBottomY());
    }
}
