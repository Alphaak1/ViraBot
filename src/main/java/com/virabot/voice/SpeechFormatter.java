package com.virabot.voice;

public final class SpeechFormatter {
    private SpeechFormatter() {
    }

    public static String buildGreeting(String channelName, String languageCode) {
        if (isJapanese(languageCode)) {
            return "ViraBotが参りました。 " + cleanForSpeech(channelName) + "で準備ができました。";
        }
        return "ViraBot has arrived. I am ready in " + cleanForSpeech(channelName) + ".";
    }

    public static String buildNowPlaying(String trackTitle, String languageCode) {
        if (isJapanese(languageCode)) {
            return "ただいま再生するのは、" + cleanForSpeech(trackTitle) + "です。";
        }
        return "Now playing, " + cleanForSpeech(trackTitle) + ".";
    }

    public static String buildSkip(String languageCode) {
        if (isJapanese(languageCode)) {
            return "現在の曲をスキップします。";
        }
        return "Skipping the current track.";
    }

    public static String buildSkipAndNowPlaying(String trackTitle, String languageCode) {
        if (isJapanese(languageCode)) {
            return "現在の曲をスキップします。ただいま再生するのは、"
                    + cleanForSpeech(trackTitle) + "です。";
        }
        return "Skipping the current track. Now playing, " + cleanForSpeech(trackTitle) + ".";
    }

    public static String buildFarewell(String languageCode) {
        if (isJapanese(languageCode)) {
            return "すべてはカタリナ様のために！";
        }
        return "All for Katalina!";
    }

    public static String cleanForSpeech(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "unknown title";
        }

        String cleaned = rawText
                .replace('&', ' ')
                .replace('_', ' ')
                .replaceAll("\\[(?i)(official|lyrics?|audio|visualizer|video|hd|4k)[^\\]]*]", "")
                .replaceAll("\\((?i)(official|lyrics?|audio|visualizer|video|hd|4k)[^)]*\\)", "")
                .replaceAll("(?i)\\bfeat\\.?\\b", "featuring")
                .replaceAll("[|/]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();

        if (cleaned.isBlank()) {
            return "unknown title";
        }

        return cleaned;
    }

    private static boolean isJapanese(String languageCode) {
        return "ja".equalsIgnoreCase(languageCode);
    }
}
