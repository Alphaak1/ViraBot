package com.virabot.voice;

import com.virabot.persistence.GuildSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class SpeechService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpeechService.class);
    private static final String LANGUAGE_ENGLISH = "en";
    private static final String LANGUAGE_JAPANESE = "ja";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String apiKey;
    private final String englishVoiceId;
    private final String japaneseVoiceId;
    private final String defaultModelId;
    private final Path tempDirectory;

    public SpeechService(
            String apiKey,
            String englishVoiceId,
            String japaneseVoiceId,
            String defaultModelId,
            Path tempDirectory
    ) {
        this.apiKey = apiKey;
        this.englishVoiceId = englishVoiceId;
        this.japaneseVoiceId = japaneseVoiceId;
        this.defaultModelId = defaultModelId;
        this.tempDirectory = tempDirectory.toAbsolutePath();

        try {
            Files.createDirectories(this.tempDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create TTS temp directory.", exception);
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && ((englishVoiceId != null && !englishVoiceId.isBlank())
                || (japaneseVoiceId != null && !japaneseVoiceId.isBlank()));
    }

    public boolean isConfiguredFor(String languageCode) {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        String voiceId = resolveVoiceId(languageCode);
        return voiceId != null && !voiceId.isBlank();
    }

    public Path synthesize(String text, GuildSettings settings) throws IOException, InterruptedException {
        String voiceId = resolveVoiceId(settings.languageCode());
        String modelId = settings.modelId();
        if (modelId == null || modelId.isBlank()) {
            modelId = defaultModelId;
        }

        if (!isConfiguredFor(settings.languageCode())) {
            throw new IllegalStateException("ElevenLabs voice is not configured for language " + settings.languageCode());
        }

        String endpoint = "https://api.elevenlabs.io/v1/text-to-speech/"
                + URLEncoder.encode(voiceId, StandardCharsets.UTF_8)
                + "?output_format=mp3_44100_128";

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .header("xi-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(buildPayload(text, modelId, settings.languageCode())))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = new String(response.body(), StandardCharsets.UTF_8);
            LOGGER.error("ElevenLabs TTS request failed with status {} and body {}", response.statusCode(), body);
            throw new IOException("ElevenLabs TTS request failed with status " + response.statusCode());
        }

        Path outputPath = Files.createTempFile(tempDirectory, "vira-", ".mp3");
        Files.write(outputPath, response.body());
        return outputPath;
    }

    private String buildPayload(String text, String modelId, String languageCode) {
        return """
                {
                  "text": "%s",
                  "model_id": "%s",
                  "language_code": "%s"
                }
                """.formatted(escapeJson(text), escapeJson(modelId), escapeJson(normalizeLanguage(languageCode)));
    }

    private String resolveVoiceId(String languageCode) {
        String normalizedLanguage = normalizeLanguage(languageCode);
        if (LANGUAGE_JAPANESE.equals(normalizedLanguage)) {
            return japaneseVoiceId;
        }
        return englishVoiceId;
    }

    private String normalizeLanguage(String languageCode) {
        return LANGUAGE_JAPANESE.equalsIgnoreCase(languageCode) ? LANGUAGE_JAPANESE : LANGUAGE_ENGLISH;
    }

    private String escapeJson(String value) {
        StringBuilder builder = new StringBuilder();
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }
}
