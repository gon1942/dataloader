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
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes duplicated image fragments embedded in table cells when a dominant
 * full-page image already represents the page. This keeps table text while
 * suppressing sliced cell images that are redundant in Markdown/HTML output.
 */
public final class DominantImageTableCellImageFilterProcessor {
    private static final double MIN_PAGE_AREA_RATIO = 0.55;
    private static final double MIN_PAGE_WIDTH_RATIO = 0.75;
    private static final double MIN_PAGE_HEIGHT_RATIO = 0.75;
    private static final double TABLE_CONTAINMENT_PADDING = 8.0;
    private static final double CELL_CONTAINMENT_PADDING = 2.0;
    private static final double MAX_IMAGE_TO_CELL_AREA_RATIO = 1.05;
    private static final double MAX_TOTAL_TINY_IMAGE_AREA_RATIO = 0.10;

    private DominantImageTableCellImageFilterProcessor() {
    }

    public static List<IObject> filterNestedTableImages(List<IObject> contents, BoundingBox pageBoundingBox) {
        if (contents == null || contents.isEmpty() || pageBoundingBox == null) {
            return contents;
        }

        ImageChunk dominantImage = findDominantImage(contents, pageBoundingBox);
        if (dominantImage == null) {
            return contents;
        }

        BoundingBox dominantBox = expand(dominantImage.getBoundingBox(), TABLE_CONTAINMENT_PADDING);
        for (IObject content : contents) {
            if (content instanceof TableBorder) {
                pruneNestedImages((TableBorder) content, dominantBox);
            }
        }
        return contents;
    }

    private static void pruneNestedImages(TableBorder table, BoundingBox dominantBox) {
        BoundingBox tableBox = table.getBoundingBox();
        if (tableBox == null || !dominantBox.contains(tableBox)) {
            return;
        }

        for (TableBorderRow row : table.getRows()) {
            for (TableBorderCell cell : row.getCells()) {
                if (cell == null) {
                    continue;
                }
                pruneCellImages(cell);
            }
        }
    }

    private static void pruneCellImages(TableBorderCell cell) {
        BoundingBox cellBox = cell.getBoundingBox();
        if (cellBox == null || cell.getContents().isEmpty()) {
            return;
        }

        double cellArea = getArea(cellBox);
        boolean hasNonImageContent = false;
        double totalImageArea = 0.0;
        for (IObject content : cell.getContents()) {
            if (content instanceof ImageChunk) {
                totalImageArea += getArea(content.getBoundingBox());
            } else {
                hasNonImageContent = true;
                break;
            }
        }

        if (!hasNonImageContent && totalImageArea > 0.0 && totalImageArea <= cellArea * MAX_TOTAL_TINY_IMAGE_AREA_RATIO) {
            cell.getContents().clear();
            return;
        }

        BoundingBox paddedCellBox = expand(cellBox, CELL_CONTAINMENT_PADDING);
        List<IObject> filtered = new ArrayList<>(cell.getContents().size());
        for (IObject content : cell.getContents()) {
            if (content instanceof ImageChunk && shouldRemoveImage((ImageChunk) content, paddedCellBox, cellArea, hasNonImageContent)) {
                continue;
            }
            filtered.add(content);
        }

        if (filtered.size() != cell.getContents().size()) {
            cell.getContents().clear();
            cell.getContents().addAll(filtered);
        }
    }

    private static boolean shouldRemoveImage(ImageChunk image, BoundingBox paddedCellBox, double cellArea,
                                             boolean hasNonImageContent) {
        BoundingBox imageBox = image.getBoundingBox();
        if (imageBox == null) {
            return true;
        }
        if (hasNonImageContent) {
            return true;
        }
        if (!paddedCellBox.contains(imageBox)) {
            return true;
        }
        return getArea(imageBox) > cellArea * MAX_IMAGE_TO_CELL_AREA_RATIO;
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
