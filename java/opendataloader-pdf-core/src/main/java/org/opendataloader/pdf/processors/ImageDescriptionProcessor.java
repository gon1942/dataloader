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

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.utils.PictureCropUtils;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ContrastRatioConsumer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opendataloader.pdf.containers.StaticLayoutContainers;

/**
 * Generates image descriptions in Java mode and upgrades ImageChunk elements to SemanticPicture.
 */
public final class ImageDescriptionProcessor {
    private static final Logger LOGGER = Logger.getLogger(ImageDescriptionProcessor.class.getCanonicalName());
    private static final double HEADER_FOOTER_ZONE_RATIO = 0.12;
    private static final double MAX_DECORATIVE_AREA_RATIO = 0.025;
    private static final double MAX_DECORATIVE_HEIGHT_RATIO = 0.18;
    private static final double MAX_DECORATIVE_WIDTH_RATIO = 0.35;
    private static final double MIN_WIDE_LOGO_ASPECT_RATIO = 2.0;

    private ImageDescriptionProcessor() {
    }

    public static void enrichDescriptions(List<List<IObject>> contents, String pdfFilePath, Config config) {
        if (!config.isImageDescriptionEnabled()) {
            return;
        }

        ContrastRatioConsumer contrastRatioConsumer =
            StaticLayoutContainers.getContrastRatioConsumer(pdfFilePath, config.getPassword(), false, null);
        if (contrastRatioConsumer == null) {
            LOGGER.log(Level.WARNING, "Image description is enabled, but page image extraction is unavailable.");
            return;
        }

        OllamaImageDescriptionClient client = new OllamaImageDescriptionClient(config);
        AtomicInteger nextPictureIndex = new AtomicInteger(1);

        for (int pageNumber = 0; pageNumber < contents.size(); pageNumber++) {
            List<IObject> pageContents = contents.get(pageNumber);
            contents.set(pageNumber, transformContents(pageContents, contrastRatioConsumer, client, config, nextPictureIndex));
        }
    }

    private static List<IObject> transformContents(List<IObject> contents,
                                                   ContrastRatioConsumer contrastRatioConsumer,
                                                   OllamaImageDescriptionClient client,
                                                   Config config,
                                                   AtomicInteger nextPictureIndex) {
        List<IObject> transformed = new ArrayList<>(contents.size());
        for (IObject content : contents) {
            transformed.add(transformObject(content, contrastRatioConsumer, client, config, nextPictureIndex));
        }
        return transformed;
    }

    private static IObject transformObject(IObject content,
                                           ContrastRatioConsumer contrastRatioConsumer,
                                           OllamaImageDescriptionClient client,
                                           Config config,
                                           AtomicInteger nextPictureIndex) {
        if (content instanceof ImageChunk) {
            return describeImage((ImageChunk) content, contrastRatioConsumer, client, config, nextPictureIndex.getAndIncrement());
        }
        if (content instanceof PDFList) {
            PDFList list = (PDFList) content;
            for (ListItem listItem : list.getListItems()) {
                List<IObject> transformed = transformContents(listItem.getContents(), contrastRatioConsumer, client, config, nextPictureIndex);
                listItem.getContents().clear();
                listItem.getContents().addAll(transformed);
            }
            return list;
        }
        if (content instanceof TableBorder) {
            TableBorder table = (TableBorder) content;
            for (TableBorderRow row : table.getRows()) {
                for (TableBorderCell cell : row.getCells()) {
                    List<IObject> transformed = transformContents(cell.getContents(), contrastRatioConsumer, client, config, nextPictureIndex);
                    cell.getContents().clear();
                    cell.getContents().addAll(transformed);
                }
            }
            return table;
        }
        if (content instanceof SemanticHeaderOrFooter) {
            SemanticHeaderOrFooter headerOrFooter = (SemanticHeaderOrFooter) content;
            List<IObject> transformed = transformContents(headerOrFooter.getContents(), contrastRatioConsumer, client, config, nextPictureIndex);
            headerOrFooter.getContents().clear();
            headerOrFooter.getContents().addAll(transformed);
            return headerOrFooter;
        }
        return content;
    }

    private static SemanticPicture describeImage(ImageChunk imageChunk,
                                                 ContrastRatioConsumer contrastRatioConsumer,
                                                 OllamaImageDescriptionClient client,
                                                 Config config,
                                                 int pictureIndex) {
        String description = null;
        if (shouldDescribeImage(imageChunk) && imageChunk.getBoundingBox() != null
            && imageChunk.getWidth() > 0 && imageChunk.getHeight() > 0) {
            try {
                String imageBase64 = toBase64Image(imageChunk, contrastRatioConsumer, config.getImageFormat());
                if (imageBase64 != null && !imageBase64.isEmpty()) {
                    description = client.describeImage(imageBase64);
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to encode image for description: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "Image description interrupted");
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Image description failed: " + e.getMessage());
            }
        }

        SemanticPicture picture = new SemanticPicture(imageChunk.getBoundingBox(), pictureIndex, description);
        picture.setRecognizedStructureId(imageChunk.getRecognizedStructureId());
        return picture;
    }

    static boolean shouldDescribeImage(ImageChunk imageChunk) {
        if (imageChunk == null || imageChunk.getBoundingBox() == null) {
            return false;
        }
        int pageIndex = Math.max(0, imageChunk.getPageNumber() - 1);
        return shouldDescribeImage(imageChunk, DocumentProcessor.getPageBoundingBox(pageIndex));
    }

    static boolean shouldDescribeImage(ImageChunk imageChunk, org.verapdf.wcag.algorithms.entities.geometry.BoundingBox pageBoundingBox) {
        if (imageChunk == null || imageChunk.getBoundingBox() == null) {
            return false;
        }
        if (pageBoundingBox == null) {
            return true;
        }

        org.verapdf.wcag.algorithms.entities.geometry.BoundingBox imageBox = imageChunk.getBoundingBox();
        double pageWidth = Math.max(0.0, pageBoundingBox.getRightX() - pageBoundingBox.getLeftX());
        double pageHeight = Math.max(0.0, pageBoundingBox.getTopY() - pageBoundingBox.getBottomY());
        double imageWidth = Math.max(0.0, imageBox.getRightX() - imageBox.getLeftX());
        double imageHeight = Math.max(0.0, imageBox.getTopY() - imageBox.getBottomY());
        if (pageWidth == 0.0 || pageHeight == 0.0 || imageWidth == 0.0 || imageHeight == 0.0) {
            return true;
        }

        double pageArea = pageWidth * pageHeight;
        double imageArea = imageWidth * imageHeight;
        double zoneHeight = pageHeight * HEADER_FOOTER_ZONE_RATIO;

        boolean inTopZone = imageBox.getBottomY() >= pageBoundingBox.getTopY() - zoneHeight;
        boolean inBottomZone = imageBox.getTopY() <= pageBoundingBox.getBottomY() + zoneHeight;
        if (!inTopZone && !inBottomZone) {
            return true;
        }

        boolean smallArea = imageArea <= pageArea * MAX_DECORATIVE_AREA_RATIO;
        boolean shortHeight = imageHeight <= pageHeight * MAX_DECORATIVE_HEIGHT_RATIO;
        boolean narrowWidth = imageWidth <= pageWidth * MAX_DECORATIVE_WIDTH_RATIO;
        boolean wideLogo = imageWidth / imageHeight >= MIN_WIDE_LOGO_ASPECT_RATIO;

        return !(smallArea && shortHeight && (narrowWidth || wideLogo));
    }

    private static String toBase64Image(ImageChunk imageChunk,
                                        ContrastRatioConsumer contrastRatioConsumer,
                                        String imageFormat) throws IOException {
        BufferedImage image = contrastRatioConsumer.getPageSubImage(
            PictureCropUtils.getCropBoundingBox(imageChunk.getBoundingBox()));
        if (image == null) {
            return null;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (!ImageIO.write(image, imageFormat, outputStream)) {
            return null;
        }
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }
}
