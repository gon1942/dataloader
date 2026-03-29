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

import org.verapdf.as.ASAtom;
import org.verapdf.cos.COSDictionary;
import org.verapdf.cos.COSObjType;
import org.verapdf.cos.COSObject;
import org.verapdf.cos.COSTrailer;
import org.verapdf.gf.model.impl.cos.GFCosInfo;
import org.verapdf.pd.PDDocument;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;

import java.io.File;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DocumentMetadataUtils {
    private static final String EXTRACTION_METHOD = "opendataloader-pdf";
    private static final Pattern PDF_DATE_PATTERN = Pattern.compile(
        "^D:(\\d{4})(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?([Zz]|([+-])(\\d{2})'?(\\d{2})'?)?$");
    private static final DateTimeFormatter ISO_DATE_TIME = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
        .optionalStart()
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .optionalEnd()
        .toFormatter();
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private DocumentMetadataUtils() {
    }

    public static DocumentMetadata getDocumentMetadata(File inputPdf, List<List<IObject>> contents) {
        PDDocument document = StaticResources.getDocument();
        GFCosInfo info = getDocumentInfo(document);
        int tableCount = countTablesInDocument(contents);
        String creationDate = formatDateIso(firstNonBlank(info.getCreationDate(), info.getXMPCreateDate()));
        String modificationDate = formatDateIso(firstNonBlank(
            info.getModDate(), info.getXMPModifyDate(), info.getCreationDate(), info.getXMPCreateDate()));
        return new DocumentMetadata(
            inputPdf.getAbsolutePath(),
            inputPdf.getName(),
            inputPdf.length(),
            document.getNumberOfPages(),
            EXTRACTION_METHOD,
            tableCount > 0,
            tableCount,
            firstNonBlank(info.getTitle(), info.getXMPTitle()),
            firstNonBlank(info.getAuthor(), info.getXMPCreator()),
            firstNonBlank(info.getSubject(), info.getXMPDescription()),
            firstNonBlank(info.getCreator(), info.getXMPCreatorTool()),
            firstNonBlank(info.getProducer(), info.getXMPProducer()),
            null,
            modificationDate != null ? modificationDate : creationDate
        );
    }

    public static List<PageMetadata> getPageMetadata(List<List<IObject>> contents) {
        PDDocument document = StaticResources.getDocument();
        List<PageMetadata> pages = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            double[] cropBox = document.getPage(pageIndex).getCropBox();
            Double width = null;
            Double height = null;
            if (cropBox != null && cropBox.length >= 4) {
                width = Math.max(0.0, cropBox[2] - cropBox[0]);
                height = Math.max(0.0, cropBox[3] - cropBox[1]);
            }
            int tableCount = pageIndex < contents.size() ? countTablesInPage(contents.get(pageIndex)) : 0;
            pages.add(new PageMetadata(pageIndex + 1, width, height, tableCount > 0, tableCount));
        }
        return pages;
    }

    public static String formatDateIso(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return null;
        }

        String formattedPdfDate = formatPdfDate(rawDate);
        if (formattedPdfDate != null) {
            return formattedPdfDate;
        }

        try {
            OffsetDateTime dateTime = OffsetDateTime.parse(rawDate);
            return dateTime.toLocalDateTime().format(ISO_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDateTime dateTime = LocalDateTime.parse(rawDate);
            return dateTime.format(ISO_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDate date = LocalDate.parse(rawDate);
            return date.format(ISO_DATE);
        } catch (DateTimeParseException ignored) {
        }

        return rawDate;
    }

    private static String formatPdfDate(String rawDate) {
        Matcher matcher = PDF_DATE_PATTERN.matcher(rawDate);
        if (!matcher.matches()) {
            return null;
        }

        int year = Integer.parseInt(matcher.group(1));
        int month = parseOrDefault(matcher.group(2), 1);
        int day = parseOrDefault(matcher.group(3), 1);
        int hour = parseOrDefault(matcher.group(4), 0);
        int minute = parseOrDefault(matcher.group(5), 0);
        int second = parseOrDefault(matcher.group(6), 0);

        try {
            LocalDateTime localDateTime = LocalDateTime.of(year, month, day, hour, minute, second);
            return localDateTime.format(ISO_DATE_TIME);
        } catch (DateTimeException ex) {
            return rawDate;
        }
    }

    private static int parseOrDefault(String value, int defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : Integer.parseInt(value);
    }

    private static GFCosInfo getDocumentInfo(PDDocument document) {
        COSTrailer trailer = document.getDocument().getTrailer();
        COSObject object = trailer.getKey(ASAtom.INFO);
        return new GFCosInfo((COSDictionary)
            (object != null && object.getType() == COSObjType.COS_DICT
                ? object.getDirectBase() : COSDictionary.construct().get()));
    }

    private static int countTablesInDocument(List<List<IObject>> contents) {
        int count = 0;
        for (List<IObject> pageContents : contents) {
            count += countTablesInPage(pageContents);
        }
        return count;
    }

    private static int countTablesInPage(List<IObject> contents) {
        int count = 0;
        for (IObject content : contents) {
            if (content instanceof TableBorder && !((TableBorder) content).isTextBlock()) {
                count++;
            }
        }
        return count;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    public static final class DocumentMetadata {
        private final String source;
        private final String fileName;
        private final long fileSize;
        private final int totalPages;
        private final String extractionMethod;
        private final boolean hasTables;
        private final int tableCount;
        private final String title;
        private final String author;
        private final String subject;
        private final String creator;
        private final String producer;
        private final String creationDate;
        private final String modificationDate;

        public DocumentMetadata(String source, String fileName, long fileSize, int totalPages, String extractionMethod,
                                boolean hasTables, int tableCount, String title, String author, String subject,
                                String creator, String producer, String creationDate, String modificationDate) {
            this.source = source;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.totalPages = totalPages;
            this.extractionMethod = extractionMethod;
            this.hasTables = hasTables;
            this.tableCount = tableCount;
            this.title = title;
            this.author = author;
            this.subject = subject;
            this.creator = creator;
            this.producer = producer;
            this.creationDate = creationDate;
            this.modificationDate = modificationDate;
        }

        public String getSource() { return source; }
        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
        public int getTotalPages() { return totalPages; }
        public String getExtractionMethod() { return extractionMethod; }
        public boolean isHasTables() { return hasTables; }
        public int getTableCount() { return tableCount; }
        public String getTitle() { return title; }
        public String getAuthor() { return author; }
        public String getSubject() { return subject; }
        public String getCreator() { return creator; }
        public String getProducer() { return producer; }
        public String getCreationDate() { return creationDate; }
        public String getModificationDate() { return modificationDate; }
    }

    public static final class PageMetadata {
        private final int page;
        private final Double width;
        private final Double height;
        private final boolean hasTables;
        private final int tableCount;

        public PageMetadata(int page, Double width, Double height, boolean hasTables, int tableCount) {
            this.page = page;
            this.width = width;
            this.height = height;
            this.hasTables = hasTables;
            this.tableCount = tableCount;
        }

        public int getPage() { return page; }
        public Double getWidth() { return width; }
        public Double getHeight() { return height; }
        public boolean isHasTables() { return hasTables; }
        public int getTableCount() { return tableCount; }
    }
}
