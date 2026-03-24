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
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a single semantic text node when it actually contains multiple numbered section headings
 * laid out on the same visual line.
 */
public final class SectionHeadingProcessor {

    private static final Pattern NUMBERED_HEADING_PATTERN = Pattern.compile("(?<!\\S)\\d+[\\.)]?\\s+\\S");
    private static final Pattern FULL_NUMBERED_HEADING_PATTERN = Pattern.compile("^\\d+[\\.)]?\\s+\\S.*$");
    private static final int MIN_SEGMENTS = 2;
    private static final int MAX_SEGMENT_LENGTH = 80;
    private static final double MIN_HORIZONTAL_GAP_RATIO = 2.5;

    private SectionHeadingProcessor() {
    }

    public static List<IObject> splitCompoundSectionHeadings(List<IObject> contents) {
        List<IObject> result = new ArrayList<>();
        for (IObject content : contents) {
            if (content instanceof SemanticTextNode) {
                List<SemanticTextNode> splitNodes = splitSemanticTextNode((SemanticTextNode) content);
                if (splitNodes != null) {
                    result.addAll(splitNodes);
                    continue;
                }
            }
            result.add(content);
        }
        return result;
    }

    private static List<SemanticTextNode> splitSemanticTextNode(SemanticTextNode textNode) {
        TextLine line = textNode.getFirstNonSpaceLine();
        if (line == null || textNode.getNonSpaceLine(1) != null) {
            return null;
        }

        String value = textNode.getValue();
        if (value == null || value.isBlank()) {
            return null;
        }

        List<int[]> segments = findHeadingSegments(value);
        if (segments.size() < MIN_SEGMENTS) {
            return null;
        }

        if (!hasWideGap(line, segments)) {
            return null;
        }

        List<SemanticTextNode> splitNodes = new ArrayList<>(segments.size());
        for (int[] segment : segments) {
            TextLine splitLine = new TextLine(line, segment[0], segment[1]);
            SemanticParagraph paragraph = new SemanticParagraph();
            paragraph.add(splitLine);
            splitNodes.add(paragraph);
        }
        return splitNodes;
    }

    private static List<int[]> findHeadingSegments(String value) {
        List<Integer> starts = new ArrayList<>();
        Matcher matcher = NUMBERED_HEADING_PATTERN.matcher(value);
        while (matcher.find()) {
            starts.add(skipWhitespace(value, matcher.start()));
        }

        List<int[]> segments = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : value.length();
            end = trimRight(value, end);
            if (start >= end) {
                return List.of();
            }

            String segmentValue = value.substring(start, end).trim();
            if (segmentValue.length() > MAX_SEGMENT_LENGTH ||
                !FULL_NUMBERED_HEADING_PATTERN.matcher(segmentValue).matches()) {
                return List.of();
            }
            segments.add(new int[]{start, end});
        }
        return segments;
    }

    private static boolean hasWideGap(TextLine line, List<int[]> segments) {
        String value = line.getValue();
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < segments.size() - 1; i++) {
            int currentEnd = segments.get(i)[1];
            int nextStart = segments.get(i + 1)[0];
            double gap = getSymbolCoordinate(line, nextStart) - getSymbolCoordinate(line, currentEnd);
            if (NodeUtils.areCloseNumbers(gap, 0) || gap < line.getFontSize() * MIN_HORIZONTAL_GAP_RATIO) {
                return false;
            }
        }
        return true;
    }

    private static int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int trimRight(String value, int index) {
        while (index > 0 && Character.isWhitespace(value.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private static double getSymbolCoordinate(TextLine line, int index) {
        try {
            return line.getSymbolStartCoordinate(index);
        } catch (NullPointerException | IndexOutOfBoundsException ex) {
            String value = line.getValue();
            int safeLength = value == null ? 0 : value.length();
            if (safeLength <= 0) {
                return line.getLeftX();
            }
            int safeIndex = Math.max(0, Math.min(index, safeLength));
            return line.getLeftX() + (line.getWidth() * safeIndex) / safeLength;
        }
    }
}
