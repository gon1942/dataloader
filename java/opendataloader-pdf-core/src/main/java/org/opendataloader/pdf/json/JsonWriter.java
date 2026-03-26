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
package org.opendataloader.pdf.json;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.opendataloader.pdf.utils.DocumentMetadataUtils;
import org.opendataloader.pdf.utils.DocumentMetadataUtils.DocumentMetadata;
import org.opendataloader.pdf.utils.DocumentMetadataUtils.PageMetadata;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JsonWriter {
    private static final Logger LOGGER = Logger.getLogger(JsonWriter.class.getCanonicalName());
    private static JsonGenerator getJsonGenerator(String fileName) throws IOException {
        JsonFactory jsonFactory = new JsonFactory();
        return jsonFactory.createGenerator(new File(fileName), JsonEncoding.UTF8)
                .setPrettyPrinter(new DefaultPrettyPrinter())
                .setCodec(ObjectMapperHolder.getObjectMapper());
    }

    public static void writeToJson(File inputPDF, String outputFolder, List<List<IObject>> contents) throws IOException {
        StaticLayoutContainers.resetImageIndex();
        String jsonFileName = outputFolder + File.separator + inputPDF.getName().substring(0, inputPDF.getName().length() - 3) + "json";
        try (JsonGenerator jsonGenerator = getJsonGenerator(jsonFileName)) {
            jsonGenerator.writeStartObject();
            writeDocumentInfo(jsonGenerator, inputPDF, contents);
            jsonGenerator.writeArrayFieldStart(JsonName.KIDS);
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                for (IObject content : contents.get(pageNumber)) {
                    if (!(content instanceof LineArtChunk)) {
                        jsonGenerator.writePOJO(content);
                    }
                }
            }

            jsonGenerator.writeEndArray();
            jsonGenerator.writeEndObject();
            LOGGER.log(Level.INFO, "Created {0}", jsonFileName);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Unable to create JSON output: " + ex.getMessage());
        }
    }

    private static void writeDocumentInfo(JsonGenerator generator, File inputPDF, List<List<IObject>> contents) throws IOException {
        DocumentMetadata metadata = DocumentMetadataUtils.getDocumentMetadata(inputPDF, contents);
        List<PageMetadata> pageMetadata = DocumentMetadataUtils.getPageMetadata(contents);

        generator.writeStringField(JsonName.FILE_NAME, metadata.getFileName());
        generator.writeNumberField(JsonName.NUMBER_OF_PAGES, metadata.getTotalPages());
        writeNullableStringField(generator, JsonName.AUTHOR, metadata.getAuthor());
        writeNullableStringField(generator, JsonName.TITLE, metadata.getTitle());
        writeNullableStringField(generator, JsonName.CREATION_DATE, metadata.getCreationDate());
        writeNullableStringField(generator, JsonName.MODIFICATION_DATE, metadata.getModificationDate());

        writeMetadata(generator, metadata);
        writePageMetadata(generator, pageMetadata);
    }

    private static void writeMetadata(JsonGenerator generator, DocumentMetadata metadata) throws IOException {
        generator.writeObjectFieldStart(JsonName.METADATA);
        generator.writeStringField(JsonName.SOURCE, metadata.getSource());
        generator.writeStringField(JsonName.FILE_NAME_SNAKE, metadata.getFileName());
        generator.writeNumberField(JsonName.FILE_SIZE, metadata.getFileSize());
        generator.writeNumberField(JsonName.TOTAL_PAGES, metadata.getTotalPages());
        generator.writeStringField(JsonName.EXTRACTION_METHOD, metadata.getExtractionMethod());
        generator.writeBooleanField(JsonName.HAS_TABLES, metadata.isHasTables());
        generator.writeNumberField(JsonName.TABLE_COUNT, metadata.getTableCount());
        writeNullableStringField(generator, JsonName.TITLE, metadata.getTitle());
        writeNullableStringField(generator, JsonName.AUTHOR, metadata.getAuthor());
        writeNullableStringField(generator, JsonName.SUBJECT, metadata.getSubject());
        writeNullableStringField(generator, JsonName.CREATOR, metadata.getCreator());
        writeNullableStringField(generator, JsonName.PRODUCER, metadata.getProducer());
        writeNullableStringField(generator, JsonName.CREATION_DATE_SNAKE, metadata.getCreationDate());
        writeNullableStringField(generator, JsonName.MODIFICATION_DATE_SNAKE, metadata.getModificationDate());
        generator.writeEndObject();
    }

    private static void writePageMetadata(JsonGenerator generator, List<PageMetadata> pageMetadata) throws IOException {
        generator.writeArrayFieldStart(JsonName.PAGES);
        for (PageMetadata page : pageMetadata) {
            generator.writeStartObject();
            generator.writeNumberField(JsonName.PAGE, page.getPage());
            if (page.getWidth() != null) {
                generator.writeNumberField(JsonName.WIDTH, page.getWidth());
            } else {
                generator.writeNullField(JsonName.WIDTH);
            }
            if (page.getHeight() != null) {
                generator.writeNumberField(JsonName.HEIGHT, page.getHeight());
            } else {
                generator.writeNullField(JsonName.HEIGHT);
            }
            generator.writeBooleanField(JsonName.HAS_TABLES, page.isHasTables());
            generator.writeNumberField(JsonName.TABLE_COUNT, page.getTableCount());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private static void writeNullableStringField(JsonGenerator generator, String fieldName, String value) throws IOException {
        if (value == null) {
            generator.writeNullField(fieldName);
        } else {
            generator.writeStringField(fieldName, value);
        }
    }
}
