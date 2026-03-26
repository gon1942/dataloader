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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MarkdownHtmlMetadataIntegrationTest {

    private static final String SAMPLE_PDF = "../../samples/pdf/lorem.pdf";

    @TempDir
    Path tempDir;

    private File samplePdf;

    @BeforeEach
    void setUp() {
        samplePdf = new File(SAMPLE_PDF);
        assumeTrue(samplePdf.exists(), "Sample PDF not found at " + samplePdf.getAbsolutePath());
    }

    @Test
    void doesNotWriteMetadataSectionToMarkdownAndHtml() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateMarkdown(true);
        config.setGenerateHtml(true);

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        String markdown = Files.readString(tempDir.resolve("lorem.md"));
        String html = Files.readString(tempDir.resolve("lorem.html"));

        assertFalse(markdown.contains("## metadata"));
        assertFalse(markdown.contains("- creation_date: "));
        assertFalse(html.contains("<section class=\"document-metadata\">"));
        assertFalse(html.contains("<dt>creation_date</dt><dd>"));
    }
}
