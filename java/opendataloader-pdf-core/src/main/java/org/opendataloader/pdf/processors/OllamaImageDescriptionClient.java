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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.json.ObjectMapperHolder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Minimal Ollama-compatible client for image description requests.
 */
public class OllamaImageDescriptionClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Config config;

    public OllamaImageDescriptionClient(Config config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getImageDescriptionTimeoutMs()))
            .build();
        this.objectMapper = ObjectMapperHolder.getObjectMapper();
    }

    public String describeImage(String imageBase64) throws IOException, InterruptedException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.getImageDescriptionModel());
        root.put("stream", false);

        ArrayNode messages = root.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", buildPrompt());
        ArrayNode images = message.putArray("images");
        images.add(imageBase64);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(config.getImageDescriptionUrl()))
            .timeout(Duration.ofMillis(config.getImageDescriptionTimeoutMs()))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                objectMapper.writeValueAsString(root), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Image description request failed with status " + response.statusCode());
        }

        JsonNode json = objectMapper.readTree(response.body());
        JsonNode messageNode = json.get("message");
        if (messageNode == null || !messageNode.isObject()) {
            return null;
        }
        JsonNode contentNode = messageNode.get("content");
        if (contentNode == null || contentNode.isNull()) {
            return null;
        }

        String content = contentNode.asText();
        return content == null ? null : content.trim();
    }

    private String buildPrompt() {
        String basePrompt = config.getImageDescriptionPrompt();
        String language = config.getImageDescriptionLanguage();
        if (language == null || language.isBlank()) {
            return basePrompt;
        }
        return basePrompt + " Respond in " + toLanguageInstruction(language) + ".";
    }

    private String toLanguageInstruction(String language) {
        String normalized = language.trim().toLowerCase();
        switch (normalized) {
            case "ko":
            case "korean":
                return "Korean";
            case "en":
            case "english":
                return "English";
            case "ja":
            case "japanese":
                return "Japanese";
            case "zh":
            case "zh-cn":
            case "zh-tw":
            case "chinese":
                return "Chinese";
            default:
                return language.trim();
        }
    }
}
