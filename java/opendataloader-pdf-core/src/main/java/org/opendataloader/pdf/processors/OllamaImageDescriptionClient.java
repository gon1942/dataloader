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
    private static final String OPENAI_CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String[] FINAL_DESCRIPTION_MARKERS = {
        "이 이미지는", "이 그림은", "본 이미지는", "이 인포그래픽은", "해당 이미지는"
    };
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
        ObjectNode root = buildRequestBody(imageBase64);

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
        if (isOpenAICompatibleUrl()) {
            return sanitizeDescription(extractOpenAICompatibleContent(json));
        }
        return sanitizeDescription(extractOllamaCompatibleContent(json));
    }

    private ObjectNode buildRequestBody(String imageBase64) {
        return isOpenAICompatibleUrl()
            ? buildOpenAICompatibleRequestBody(imageBase64)
            : buildOllamaCompatibleRequestBody(imageBase64);
    }

    private ObjectNode buildOllamaCompatibleRequestBody(String imageBase64) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.getImageDescriptionModel());
        root.put("stream", false);

        ArrayNode messages = root.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", buildPrompt());
        ArrayNode images = message.putArray("images");
        images.add(imageBase64);
        return root;
    }

    private ObjectNode buildOpenAICompatibleRequestBody(String imageBase64) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.getImageDescriptionModel());
        root.put("stream", false);

        ArrayNode messages = root.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");

        ArrayNode content = message.putArray("content");
        ObjectNode textPart = content.addObject();
        textPart.put("type", "text");
        textPart.put("text", buildPrompt());

        ObjectNode imagePart = content.addObject();
        imagePart.put("type", "image_url");
        ObjectNode imageUrl = imagePart.putObject("image_url");
        imageUrl.put("url", "data:image/png;base64," + imageBase64);
        return root;
    }

    private String extractOllamaCompatibleContent(JsonNode json) {
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

    private String extractOpenAICompatibleContent(JsonNode json) {
        JsonNode choicesNode = json.get("choices");
        if (choicesNode == null || !choicesNode.isArray() || choicesNode.isEmpty()) {
            return null;
        }

        JsonNode messageNode = choicesNode.get(0).get("message");
        if (messageNode == null || !messageNode.isObject()) {
            return null;
        }

        JsonNode contentNode = messageNode.get("content");
        if (contentNode == null || contentNode.isNull()) {
            return null;
        }

        if (contentNode.isTextual()) {
            String content = contentNode.asText();
            return content == null ? null : content.trim();
        }

        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : contentNode) {
                if (!item.isObject()) {
                    continue;
                }
                JsonNode typeNode = item.get("type");
                if (typeNode == null || !typeNode.isTextual()) {
                    continue;
                }
                if (!"text".equals(typeNode.asText())) {
                    continue;
                }
                JsonNode textNode = item.get("text");
                if (textNode == null || textNode.isNull()) {
                    continue;
                }
                String text = textNode.asText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(text.trim());
            }
            return builder.length() == 0 ? null : builder.toString();
        }

        return null;
    }

    private boolean isOpenAICompatibleUrl() {
        String url = config.getImageDescriptionUrl();
        return url != null && url.contains(OPENAI_CHAT_COMPLETIONS_PATH);
    }

    private String sanitizeDescription(String content) {
        if (content == null) {
            return null;
        }

        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) {
            return null;
        }

        int finalDescriptionStart = -1;
        for (String marker : FINAL_DESCRIPTION_MARKERS) {
            int markerIndex = normalized.lastIndexOf(marker);
            if (markerIndex > finalDescriptionStart) {
                finalDescriptionStart = markerIndex;
            }
        }
        if (finalDescriptionStart >= 0) {
            normalized = normalized.substring(finalDescriptionStart).trim();
        }

        if (normalized.startsWith("thought")) {
            int firstNewline = normalized.indexOf('\n');
            if (firstNewline >= 0) {
                normalized = normalized.substring(firstNewline + 1).trim();
            }
        }

        return normalized.isEmpty() ? null : normalized;
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
