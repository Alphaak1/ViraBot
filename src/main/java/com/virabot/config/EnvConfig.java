package com.virabot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EnvConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnvConfig.class);
    private static final Path ENV_FILE = Path.of(".env");
    private static final Map<String, String> FILE_VALUES = new ConcurrentHashMap<>();
    private static volatile boolean loaded;

    private EnvConfig() {
    }

    public static String get(String key) {
        loadIfNeeded();

        String environmentValue = System.getenv(key);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }

        return FILE_VALUES.get(key);
    }

    private static void loadIfNeeded() {
        if (loaded) {
            return;
        }

        synchronized (EnvConfig.class) {
            if (loaded) {
                return;
            }

            if (!Files.exists(ENV_FILE)) {
                loaded = true;
                return;
            }

            try {
                for (String line : Files.readAllLines(ENV_FILE)) {
                    String trimmedLine = line.trim();
                    if (trimmedLine.isBlank() || trimmedLine.startsWith("#")) {
                        continue;
                    }

                    int separatorIndex = trimmedLine.indexOf('=');
                    if (separatorIndex <= 0) {
                        continue;
                    }

                    String key = trimmedLine.substring(0, separatorIndex).trim();
                    String value = trimmedLine.substring(separatorIndex + 1).trim();
                    FILE_VALUES.put(key, stripQuotes(value));
                }
            } catch (IOException exception) {
                LOGGER.warn("Failed to read {}", ENV_FILE.toAbsolutePath(), exception);
            } finally {
                loaded = true;
            }
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
