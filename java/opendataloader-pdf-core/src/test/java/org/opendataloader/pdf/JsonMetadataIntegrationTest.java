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
package org.opendataloader.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JsonMetadataIntegrationTest {

    private static final String SAMPLE_PDF = "../../samples/pdf/lorem.pdf";
    private static final String OUTPUT_BASENAME = "lorem.json";

    @TempDir
    Path tempDir;

    private File samplePdf;

    @BeforeEach
    void setUp() {
        samplePdf = new File(SAMPLE_PDF);
        assumeTrue(samplePdf.exists(), "Sample PDF not found at " + samplePdf.getAbsolutePath());
    }

    @Test
    void writesAnalysisStyleMetadata() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(OUTPUT_BASENAME);
        assertTrue(Files.exists(jsonOutput), "JSON output should exist");

        JsonNode root = new ObjectMapper().readTree(Files.newInputStream(jsonOutput));
        JsonNode metadata = root.get("metadata");
        assertNotNull(metadata, "metadata block must exist");
        assertEquals(samplePdf.getAbsolutePath(), metadata.get("source").asText());
        assertEquals(samplePdf.getName(), metadata.get("file_name").asText());
        assertEquals(samplePdf.length(), metadata.get("file_size").asLong());
        assertEquals(root.get("number of pages").asInt(), metadata.get("total_pages").asInt());
        assertEquals("opendataloader-pdf", metadata.get("extraction_method").asText());
        assertTrue(root.get("creation date").asText().contains("T"));
        assertTrue(metadata.get("creation_date").asText().contains("T"));
        assertTrue(metadata.has("has_tables"));
        assertTrue(metadata.has("table_count"));

        JsonNode pages = root.get("pages");
        assertNotNull(pages, "pages metadata must exist");
        assertTrue(pages.isArray(), "pages must be an array");
        assertEquals(root.get("number of pages").asInt(), pages.size());

        JsonNode firstPage = pages.get(0);
        assertEquals(1, firstPage.get("page").asInt());
        assertTrue(firstPage.get("width").asDouble() > 0.0);
        assertTrue(firstPage.get("height").asDouble() > 0.0);
        assertTrue(firstPage.has("has_tables"));
        assertTrue(firstPage.has("table_count"));
    }
}
