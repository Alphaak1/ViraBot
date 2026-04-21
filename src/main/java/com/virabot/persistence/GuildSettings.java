package com.virabot.persistence;

public record GuildSettings(
        long guildId,
        boolean greetingEnabled,
        boolean announcementsEnabled,
        String languageCode,
        String voiceProvider,
        String voiceId,
        String modelId
) {
}
