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
 * Merges image chunks that are visually part of the same figure.
 */
public final class ImageMergeProcessor {
    private static final double MERGE_PADDING = 8.0;

    private ImageMergeProcessor() {
    }

    public static List<IObject> mergeImages(List<IObject> contents) {
        List<IObject> result = new ArrayList<>();
        boolean[] consumed = new boolean[contents.size()];

        for (int i = 0; i < contents.size(); i++) {
            if (consumed[i]) {
                continue;
            }

            IObject content = contents.get(i);
            if (!(content instanceof ImageChunk)) {
                result.add(content);
                continue;
            }

            BoundingBox mergedBox = new BoundingBox(content.getBoundingBox());
            Long recognizedStructureId = content.getRecognizedStructureId();
            consumed[i] = true;

            boolean changed;
            do {
                changed = false;
                for (int j = i + 1; j < contents.size(); j++) {
                    if (consumed[j]) {
                        continue;
                    }
                    IObject candidate = contents.get(j);
                    if (!(candidate instanceof ImageChunk)) {
                        continue;
                    }
                    if (shouldMerge(mergedBox, candidate.getBoundingBox())) {
                        mergedBox = BoundingBox.union(mergedBox, candidate.getBoundingBox());
                        consumed[j] = true;
                        changed = true;
                    }
                }
            } while (changed);

            ImageChunk merged = new ImageChunk(mergedBox);
            if (recognizedStructureId != null) {
                merged.setRecognizedStructureId(recognizedStructureId);
            }
            result.add(merged);
        }

        return result;
    }

    private static boolean shouldMerge(BoundingBox first, BoundingBox second) {
        if (first == null || second == null) {
            return false;
        }
        if (!first.getPageNumber().equals(second.getPageNumber())) {
            return false;
        }
        if (first.contains(second) || second.contains(first) || first.overlaps(second) || second.overlaps(first)) {
            return true;
        }

        BoundingBox expandedFirst = expand(first);
        BoundingBox expandedSecond = expand(second);
        return expandedFirst.overlaps(expandedSecond) || expandedSecond.overlaps(expandedFirst);
    }

    private static BoundingBox expand(BoundingBox box) {
        BoundingBox expanded = new BoundingBox(box);
        expanded.setLeftX(expanded.getLeftX() - MERGE_PADDING);
        expanded.setRightX(expanded.getRightX() + MERGE_PADDING);
        expanded.setBottomY(expanded.getBottomY() - MERGE_PADDING);
        expanded.setTopY(expanded.getTopY() + MERGE_PADDING);
        return expanded;
    }
}
