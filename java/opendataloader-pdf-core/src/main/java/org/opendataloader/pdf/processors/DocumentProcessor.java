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

import com.fasterxml.jackson.databind.JsonNode;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.processors.readingorder.XYCutPlusPlusSorter;
import org.opendataloader.pdf.json.JsonWriter;
import org.opendataloader.pdf.markdown.MarkdownGenerator;
import org.opendataloader.pdf.markdown.MarkdownGeneratorFactory;
import org.opendataloader.pdf.markdown.MarkdownSyntax;
import org.opendataloader.pdf.html.HtmlGenerator;
import org.opendataloader.pdf.html.HtmlGeneratorFactory;
import org.opendataloader.pdf.pdf.PDFWriter;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.text.TextGenerator;
import org.opendataloader.pdf.utils.ContentSanitizer;
import org.opendataloader.pdf.utils.ImagesUtils;
import org.verapdf.as.ASAtom;
import org.verapdf.containers.StaticCoreContainers;
import org.verapdf.cos.COSDictionary;
import org.verapdf.cos.COSObjType;
import org.verapdf.cos.COSObject;
import org.verapdf.cos.COSTrailer;
import org.verapdf.gf.model.impl.containers.StaticStorages;
import org.verapdf.gf.model.impl.cos.GFCosInfo;
import org.verapdf.gf.model.impl.sa.GFSAPDFDocument;
import org.verapdf.parser.PDFFlavour;
import org.verapdf.pd.PDDocument;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.LinesPreprocessingConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.xmp.containers.StaticXmpCoreContainers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main processor for PDF document analysis and output generation.
 * Coordinates the extraction, processing, and generation of various output formats.
 */
public class DocumentProcessor {
    private static final Logger LOGGER = Logger.getLogger(DocumentProcessor.class.getCanonicalName());

    /**
     * Processes a PDF file and generates the configured outputs.
     *
     * @param inputPdfName the path to the input PDF file
     * @param config the configuration settings
     * @throws IOException if unable to process the file
     */
    public static void processFile(String inputPdfName, Config config) throws IOException {
        preprocessing(inputPdfName, config);
        calculateDocumentInfo();
        Set<Integer> pagesToProcess = getValidPageNumbers(config);
        List<List<IObject>> contents;
        if (StaticLayoutContainers.isUseStructTree()) {
            contents = TaggedDocumentProcessor.processDocument(inputPdfName, config, pagesToProcess);
        } else if (config.isHybridEnabled()) {
            contents = HybridDocumentProcessor.processDocument(inputPdfName, config, pagesToProcess);
        } else {
            contents = processDocument(inputPdfName, config, pagesToProcess);
        }
        sortContents(contents, config);
        ContentSanitizer contentSanitizer = new ContentSanitizer(config.getFilterConfig().getFilterRules(),
            config.getFilterConfig().isFilterSensitiveData());
        contentSanitizer.sanitizeContents(contents);
        generateOutputs(inputPdfName, contents, config);
    }

    /**
     * Validates and filters page numbers from config against actual document pages.
     * Logs warnings for pages that don't exist in the document.
     *
     * @param config the configuration containing page selection
     * @return Set of valid 0-indexed page numbers to process, or null for all pages
     */
    private static Set<Integer> getValidPageNumbers(Config config) {
        List<Integer> requestedPages = config.getPageNumbers();
        if (requestedPages.isEmpty()) {
            return null; // null means process all pages
        }

        int totalPages = StaticContainers.getDocument().getNumberOfPages();
        Set<Integer> validPages = new LinkedHashSet<>();
        List<Integer> invalidPages = new ArrayList<>();

        for (Integer page : requestedPages) {
            int zeroIndexed = page - 1; // Convert 1-based to 0-based
            if (zeroIndexed >= 0 && zeroIndexed < totalPages) {
                validPages.add(zeroIndexed);
            } else {
                invalidPages.add(page);
            }
        }

        if (!invalidPages.isEmpty()) {
            LOGGER.log(Level.WARNING,
                "Requested pages {0} do not exist in document (total pages: {1}). Processing only existing pages: {2}",
                new Object[]{invalidPages, totalPages,
                    validPages.stream().map(p -> p + 1).collect(Collectors.toList())});
        }

        if (validPages.isEmpty()) {
            LOGGER.log(Level.WARNING,
                "No valid pages to process. Document has {0} pages but requested: {1}",
                new Object[]{totalPages, requestedPages});
        }

        return validPages;
    }

    private static List<List<IObject>> processDocument(String inputPdfName, Config config, Set<Integer> pagesToProcess) throws IOException {
        List<List<IObject>> contents = new ArrayList<>();
        int totalPages = StaticContainers.getDocument().getNumberOfPages();
        for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
            if (shouldProcessPage(pageNumber, pagesToProcess)) {
                LOGGER.log(Level.INFO, "Extracting raw contents for page {0}", pageNumber + 1);
                List<IObject> pageContents = ContentFilterProcessor.getFilteredContents(inputPdfName,
                    StaticContainers.getDocument().getArtifacts(pageNumber), pageNumber, config);
                logPageContents("After content filtering", pageNumber, pageContents);
                contents.add(pageContents);
            } else {
                contents.add(new ArrayList<>()); // Empty placeholder for skipped pages
            }
        }
        if (config.isClusterTableMethod()) {
            LOGGER.log(Level.INFO, "Running clustered table detection");
            new ClusterTableProcessor().processTables(contents);
            logDocumentContents("After clustered table detection", contents);
        }
        for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
            if (!shouldProcessPage(pageNumber, pagesToProcess)) {
                continue;
            }
            LOGGER.log(Level.INFO, "Post-processing extracted contents for page {0}", pageNumber + 1);
            List<IObject> pageContents = TableBorderProcessor.processTableBorders(contents.get(pageNumber), pageNumber);
            logPageContents("After table border processing", pageNumber, pageContents);
            if (config.isDetectStrikethrough()) {
                StrikethroughProcessor.processStrikethroughs(pageContents);
                logPageContents("After strikethrough detection", pageNumber, pageContents);
            }
            pageContents = pageContents.stream().filter(x -> !(x instanceof LineChunk)).collect(Collectors.toList());
            logPageContents("After line chunk filtering", pageNumber, pageContents);
            pageContents = TextLineProcessor.processTextLines(pageContents);
            logPageContents("After text line processing", pageNumber, pageContents);
            pageContents = AlignedTextTableProcessor.detectAlignedTextTables(pageContents);
            logPageContents("After aligned text table detection", pageNumber, pageContents);
            pageContents = SpecialTableProcessor.detectSpecialTables(pageContents);
            logPageContents("After special table detection", pageNumber, pageContents);
            contents.set(pageNumber, pageContents);
        }
        LOGGER.log(Level.INFO, "Running header/footer detection");
        HeaderFooterProcessor.processHeadersAndFooters(contents, false);
        logDocumentContents("After header/footer detection", contents);
        LOGGER.log(Level.INFO, "Running list detection");
        ListProcessor.processLists(contents, false);
        logDocumentContents("After list detection", contents);
        for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
            if (!shouldProcessPage(pageNumber, pagesToProcess)) {
                continue;
            }
            List<IObject> pageContents = contents.get(pageNumber);
            LOGGER.log(Level.INFO, "Building semantic structure for page {0}", pageNumber + 1);
            pageContents = ParagraphProcessor.processParagraphs(pageContents);
            logPageContents("After paragraph detection", pageNumber, pageContents);
            pageContents = SectionHeadingProcessor.splitCompoundSectionHeadings(pageContents);
            logPageContents("After compound heading split", pageNumber, pageContents);
            pageContents = ListProcessor.processListsFromTextNodes(pageContents);
            logPageContents("After text-node list detection", pageNumber, pageContents);
            HeadingProcessor.processHeadings(pageContents, false);
            logPageContents("After heading detection", pageNumber, pageContents);
            setIDs(pageContents);
            logPageContents("After assigning IDs", pageNumber, pageContents);
            CaptionProcessor.processCaptions(pageContents);
            logPageContents("After caption detection", pageNumber, pageContents);
            contents.set(pageNumber, pageContents);
        }
        LOGGER.log(Level.INFO, "Reconciling cross-page semantic structures");
        ListProcessor.checkNeighborLists(contents);
        TableBorderProcessor.checkNeighborTables(contents);
        HeadingProcessor.detectHeadingsLevels();
        LevelProcessor.detectLevels(contents);
        logDocumentContents("After semantic reconciliation", contents);
        return contents;
    }

    /**
     * Checks if a page should be processed based on the filter.
     *
     * @param pageNumber 0-indexed page number
     * @param pagesToProcess set of valid page numbers to process, or null for all pages
     * @return true if the page should be processed
     */
    private static boolean shouldProcessPage(int pageNumber, Set<Integer> pagesToProcess) {
        return pagesToProcess == null || pagesToProcess.contains(pageNumber);
    }

    private static void logPageContents(String stage, int pageNumber, List<IObject> pageContents) {
        LOGGER.log(Level.INFO, "{0} on page {1}: {2} objects",
            new Object[]{stage, pageNumber + 1, pageContents.size()});
        if (!pageContents.isEmpty()) {
            LOGGER.log(Level.INFO, "{0} on page {1} types: {2}",
                new Object[]{stage, pageNumber + 1, summarizeTypes(pageContents)});
            LOGGER.log(Level.INFO, "{0} on page {1} samples: {2}",
                new Object[]{stage, pageNumber + 1, summarizeSamples(pageContents)});
        }
    }

    private static void logDocumentContents(String stage, List<List<IObject>> contents) {
        int totalObjects = contents.stream().mapToInt(List::size).sum();
        LOGGER.log(Level.INFO, "{0}: {1} pages, {2} objects",
            new Object[]{stage, contents.size(), totalObjects});
        if (totalObjects > 0) {
            List<IObject> flattenedContents = contents.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
            LOGGER.log(Level.INFO, "{0} types: {1}",
                new Object[]{stage, summarizeTypes(flattenedContents)});
            LOGGER.log(Level.INFO, "{0} samples: {1}",
                new Object[]{stage, summarizeSamples(flattenedContents)});
        }
    }

    private static String summarizeTypes(List<IObject> contents) {
        return contents.stream()
            .collect(Collectors.groupingBy(DocumentProcessor::getObjectTypeName, LinkedHashMap::new, Collectors.counting()))
            .entrySet()
            .stream()
            .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
            .limit(8)
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(", "));
    }

    private static String summarizeSamples(List<IObject> contents) {
        return contents.stream()
            .limit(5)
            .map(DocumentProcessor::describeObject)
            .collect(Collectors.joining(" | "));
    }

    private static String describeObject(IObject object) {
        String typeName = getObjectTypeName(object);
        StringBuilder description = new StringBuilder(typeName);
        if (object.getRecognizedStructureId() != null) {
            description.append("#").append(object.getRecognizedStructureId());
        }
        if (object.getBoundingBox() != null) {
            BoundingBox boundingBox = object.getBoundingBox();
            description.append("@p").append(boundingBox.getPageNumber() + 1)
                .append("[")
                .append(formatCoordinate(boundingBox.getLeftX())).append(",")
                .append(formatCoordinate(boundingBox.getTopY())).append(" -> ")
                .append(formatCoordinate(boundingBox.getRightX())).append(",")
                .append(formatCoordinate(boundingBox.getBottomY())).append("]");
        }
        if (object instanceof SemanticTextNode) {
            String value = ((SemanticTextNode) object).getValue();
            if (value != null && !value.isEmpty()) {
                description.append(" \"").append(abbreviate(value)).append("\"");
            }
        }
        return description.toString();
    }

    private static String getObjectTypeName(IObject object) {
        return object.getClass().getSimpleName();
    }

    private static String abbreviate(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 77) + "...";
    }

    private static String formatCoordinate(Double value) {
        return value == null ? "null" : String.format(Locale.ROOT, "%.1f", value);
    }

    private static void generateOutputs(String inputPdfName, List<List<IObject>> contents, Config config) throws IOException {
        File inputPDF = new File(inputPdfName);
        new File(config.getOutputFolder()).mkdirs();
        if (!config.isImageOutputOff() && (config.isGenerateHtml() || config.isGenerateMarkdown() || config.isGenerateJSON())) {
            String imagesDirectory;
            if (config.getImageDir() != null && !config.getImageDir().isEmpty()) {
                imagesDirectory = config.getImageDir();
            } else {
                String fileName = Paths.get(inputPdfName).getFileName().toString();
                String baseName = fileName.substring(0, fileName.length() - 4);
                imagesDirectory = config.getOutputFolder() + File.separator + baseName + MarkdownSyntax.IMAGES_DIRECTORY_SUFFIX;
            }
            StaticLayoutContainers.setImagesDirectory(imagesDirectory);
            ImagesUtils imagesUtils = new ImagesUtils();
            imagesUtils.write(contents, inputPdfName, config.getPassword());
        }
        if (config.isGeneratePDF()) {
            PDFWriter pdfWriter = new PDFWriter();
            pdfWriter.updatePDF(inputPDF, config.getPassword(), config.getOutputFolder(), contents);
        }
        if (config.isGenerateJSON()) {
            String jsonOutputPath = getOutputFilePath(inputPDF, config.getOutputFolder(), "json");
            LOGGER.log(Level.INFO, "Starting JSON output generation: {0}", jsonOutputPath);
            JsonWriter.writeToJson(inputPDF, config.getOutputFolder(), contents);
            LOGGER.log(Level.INFO, "Finished JSON output generation: {0}", jsonOutputPath);
        }
        if (config.isGenerateMarkdown()) {
            try (MarkdownGenerator markdownGenerator = MarkdownGeneratorFactory.getMarkdownGenerator(inputPDF,
                config)) {
                markdownGenerator.writeToMarkdown(contents);
            }
        }
        if (config.isGenerateHtml()) {
            try (HtmlGenerator htmlGenerator = HtmlGeneratorFactory.getHtmlGenerator(inputPDF, config)) {
                htmlGenerator.writeToHtml(contents);
            }
        }
        if (config.isGenerateText()) {
            try (TextGenerator textGenerator = new TextGenerator(inputPDF, config)) {
                textGenerator.writeToText(contents);
            }
        }
        writeHybridRawOutputsIfPresent(config.getOutputFolder());
    }

    private static String getOutputFilePath(File inputPDF, String outputFolder, String extension) {
        String baseName = inputPDF.getName().substring(0, inputPDF.getName().length() - 4);
        return outputFolder + File.separator + baseName + "." + extension;
    }

    private static void writeHybridRawOutputsIfPresent(String outputFolder) {
        JsonNode rawPayload = HybridDocumentProcessor.consumeLastRawPayload();
        if (rawPayload == null || rawPayload.isNull()) {
            return;
        }

        Path rawDir = Path.of(outputFolder, "_paddle_raw");
        try {
            Files.createDirectories(rawDir);
            Files.writeString(rawDir.resolve("paddle_raw.json"),
                rawPayload.toPrettyString(), StandardCharsets.UTF_8);

            JsonNode pagesNode = rawPayload.get("pages");
            if (pagesNode != null && pagesNode.isArray()) {
                int pageIndex = 1;
                for (JsonNode pageNode : pagesNode) {
                    JsonNode markdownNode = pageNode.get("markdown");
                    if (markdownNode == null || !markdownNode.isObject()) {
                        pageIndex++;
                        continue;
                    }
                    JsonNode markdownTextNode = markdownNode.get("markdown_texts");
                    if (markdownTextNode == null || !markdownTextNode.isTextual()) {
                        markdownTextNode = markdownNode.get("text");
                    }
                    if (markdownTextNode != null && markdownTextNode.isTextual()) {
                        Files.writeString(
                            rawDir.resolve(String.format(Locale.ROOT, "page_%04d.md", pageIndex)),
                            markdownTextNode.asText(),
                            StandardCharsets.UTF_8
                        );
                    }
                    pageIndex++;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to write hybrid raw outputs: {0}", e.getMessage());
        }
    }

    /**
     * Performs preprocessing on a PDF document.
     * Initializes static containers and parses the document structure.
     *
     * @param pdfName the path to the PDF file
     * @param config the configuration settings
     * @throws IOException if unable to read the PDF file
     */
    public static void preprocessing(String pdfName, Config config) throws IOException {
        LOGGER.log(Level.INFO, () -> "File name: " + pdfName);
        updateStaticContainers(config);
        PDDocument pdDocument = new PDDocument(pdfName);
        StaticResources.setDocument(pdDocument);
        GFSAPDFDocument document = new GFSAPDFDocument(pdDocument);
//        org.verapdf.gf.model.impl.containers.StaticContainers.setFlavour(Collections.singletonList(PDFAFlavour.WCAG_2_2));
        StaticResources.setFlavour(Collections.singletonList(PDFFlavour.WCAG_2_2_HUMAN));
        StaticStorages.setIsFilterInvisibleLayers(config.getFilterConfig().isFilterHiddenOCG());
        StaticContainers.setDocument(document);
        if (config.isUseStructTree()) {
            document.parseStructureTreeRoot();
            if (document.getTree() != null) {
                StaticLayoutContainers.setIsUseStructTree(true);
            } else {
                StaticLayoutContainers.setIsUseStructTree(false);
                LOGGER.log(Level.WARNING, "The document has no structure tree. The 'use-struct-tree' option will be ignored.");
            }
        }
        StaticContainers.setIsDataLoader(true);
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticResources.setIsFontProgramsParsing(true);
        StaticStorages.setIsIgnoreMCIDs(!StaticLayoutContainers.isUseStructTree());
        StaticStorages.setIsAddSpacesBetweenTextPieces(true);
        document.parseChunks();
        LinesPreprocessingConsumer linesPreprocessingConsumer = new LinesPreprocessingConsumer();
        linesPreprocessingConsumer.findTableBorders();
        StaticContainers.setTableBordersCollection(new TableBordersCollection(linesPreprocessingConsumer.getTableBorders()));
    }

    private static void updateStaticContainers(Config config) {
        StaticResources.clear();
        StaticContainers.updateContainers(null);
        StaticLayoutContainers.clearContainers();
        org.verapdf.gf.model.impl.containers.StaticContainers.clearAllContainers();
        StaticCoreContainers.clearAllContainers();
        StaticXmpCoreContainers.clearAllContainers();
        StaticContainers.setKeepLineBreaks(config.isKeepLineBreaks());
        StaticLayoutContainers.setCurrentContentId(1);
        StaticLayoutContainers.setEmbedImages(config.isEmbedImages());
        StaticLayoutContainers.setImageFormat(config.getImageFormat());
        StaticResources.setPassword(config.getPassword());
    }

    /**
     * Assigns unique IDs to each content object.
     *
     * @param contents the list of content objects
     */
    public static void setIDs(List<IObject> contents) {
        for (IObject object : contents) {
            object.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        }
    }

    /**
     * Sets index values for all content objects across all pages.
     *
     * @param contents the document contents organized by page
     */
    public static void setIndexesForDocumentContents(List<List<IObject>> contents) {
        for (List<IObject> pageContents : contents) {
            setIndexesForContentsList(pageContents);
        }
    }

    /**
     * Sets index values for content objects in a list.
     *
     * @param contents the list of content objects
     */
    public static void setIndexesForContentsList(List<IObject> contents) {
        for (int index = 0; index < contents.size(); index++) {
            contents.get(index).setIndex(index);
        }
    }

    /**
     * Creates a new list with null objects removed.
     *
     * @param contents the list that may contain null objects
     * @return a new list without null objects
     */
    public static List<IObject> removeNullObjectsFromList(List<IObject> contents) {
        List<IObject> newContents = new ArrayList<>();
        for (IObject content : contents) {
            if (content != null) {
                newContents.add(content);
            }
        }
        return newContents;
    }

    private static void calculateDocumentInfo() {
        PDDocument document = StaticResources.getDocument();
        LOGGER.log(Level.INFO, () -> "Number of pages: " + document.getNumberOfPages());
        COSTrailer trailer = document.getDocument().getTrailer();
        GFCosInfo info = getInfo(trailer);
        LOGGER.log(Level.INFO, () -> "Author: " + (info.getAuthor() != null ? info.getAuthor() : info.getXMPCreator()));
        LOGGER.log(Level.INFO, () -> "Title: " + (info.getTitle() != null ? info.getTitle() : info.getXMPTitle()));
        LOGGER.log(Level.INFO, () -> "Creation date: " + (info.getCreationDate() != null ? info.getCreationDate() : info.getXMPCreateDate()));
        LOGGER.log(Level.INFO, () -> "Modification date: " + (info.getModDate() != null ? info.getModDate() : info.getXMPModifyDate()));
    }

    private static GFCosInfo getInfo(COSTrailer trailer) {
        COSObject object = trailer.getKey(ASAtom.INFO);
        return new GFCosInfo((COSDictionary) (object != null && object.getType() == COSObjType.COS_DICT ? object.getDirectBase() : COSDictionary.construct().get()));
    }

    /**
     * Gets a debug string representation of a text node.
     *
     * @param textNode the text node to describe
     * @return a string with font, size, color, and content information
     */
    public static String getContentsValueForTextNode(SemanticTextNode textNode) {
        return String.format("%s: font %s, text size %.2f, text color %s, text content \"%s\"",
                textNode.getSemanticType().getValue(), textNode.getFontName(),
                textNode.getFontSize(), Arrays.toString(textNode.getTextColor()),
                textNode.getValue().length() > 15 ? textNode.getValue().substring(0, 15) + "..." : textNode.getValue());
    }

    /**
     * Gets the bounding box for a page.
     *
     * @param pageNumber the page number (0-indexed)
     * @return the page bounding box, or null if not available
     */
    public static BoundingBox getPageBoundingBox(int pageNumber) {
        PDDocument document = StaticResources.getDocument();
        if (document == null) {
            return null;
        }
        double[] cropBox = document.getPage(pageNumber).getCropBox();
        if (cropBox == null) {
            return null;
        }
        return new BoundingBox(pageNumber, cropBox);
    }

    /**
     * Sorts page contents by their bounding box positions.
     *
     * @param contents the list of content objects to sort
     * @return a new sorted list of content objects
     */
    public static List<IObject> sortPageContents(List<IObject> contents) {
        if (contents == null || contents.isEmpty()) {
            return contents;
        }
        List<IObject> sortedContents = new ArrayList<>(contents);
        sortedContents.sort((o1, o2) -> {
            BoundingBox b1 = o1.getBoundingBox();
            BoundingBox b2 = o2.getBoundingBox();
            if (b1 == null && b2 == null) {
                return 0;
            }
            if (b1 == null) {
                return 1;
            }
            if (b2 == null) {
                return -1;
            }
            if (!Objects.equals(b1.getPageNumber(), b2.getPageNumber())) {
                return b1.getPageNumber() - b2.getPageNumber();
            }
            if (!Objects.equals(b1.getLastPageNumber(), b2.getLastPageNumber())) {
                return b1.getLastPageNumber() - b2.getLastPageNumber();
            }
            if (!Objects.equals(b1.getTopY(), b2.getTopY())) {
                return b2.getTopY() - b1.getTopY() > 0 ? 1 : -1;
            }
            if (!Objects.equals(b1.getLeftX(), b2.getLeftX())) {
                return b1.getLeftX() - b2.getLeftX() > 0 ? 1 : -1;
            }
            if (!Objects.equals(b1.getBottomY(), b2.getBottomY())) {
                return b1.getBottomY() - b2.getBottomY() > 0 ? 1 : -1;
            }
            if (!Objects.equals(b1.getRightX(), b2.getRightX())) {
                return b1.getRightX() - b2.getRightX() > 0 ? 1 : -1;
            }
            return 0;
        });
        return sortedContents;
    }

    /**
     * Sorts document contents according to the configured reading order.
     *
     * @param contents the document contents organized by page
     * @param config the configuration containing reading order settings
     */
    public static void sortContents(List<List<IObject>> contents, Config config) {
        String readingOrder = config.getReadingOrder();
        LOGGER.log(Level.INFO, "Applying reading order: {0}", readingOrder);
        logDocumentOrder("Before reading-order sort", contents);

        // xycut: XY-Cut++ sorting
        if (Config.READING_ORDER_XYCUT.equals(readingOrder)) {
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                contents.set(pageNumber, XYCutPlusPlusSorter.sort(contents.get(pageNumber)));
            }
            logDocumentOrder("After reading-order sort", contents);
            return;
        }

        // Log warning for unknown reading order values
        if (!Config.READING_ORDER_OFF.equals(readingOrder)) {
            LOGGER.log(Level.WARNING, "Unknown reading order value ''{0}'', using default ''off''", readingOrder);
        }

        // off: skip sorting (keep PDF COS object order)
        logDocumentOrder("Reading-order sort skipped", contents);
    }

    private static void logDocumentOrder(String stage, List<List<IObject>> contents) {
        for (int pageNumber = 0; pageNumber < contents.size(); pageNumber++) {
            List<IObject> pageContents = contents.get(pageNumber);
            if (pageContents.isEmpty()) {
                continue;
            }
            LOGGER.log(Level.INFO, "{0} on page {1}: {2}",
                new Object[]{stage, pageNumber + 1, summarizeSamples(pageContents)});
        }
    }
}
