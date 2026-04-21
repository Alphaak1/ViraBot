package com.virabot.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

public final class GuildSettingsRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildSettingsRepository.class);

    private final DatabaseManager databaseManager;
    private final String defaultVoiceId;
    private final String defaultModelId;

    public GuildSettingsRepository(DatabaseManager databaseManager, String defaultVoiceId, String defaultModelId) {
        this.databaseManager = databaseManager;
        this.defaultVoiceId = defaultVoiceId;
        this.defaultModelId = defaultModelId;
    }

    public GuildSettings getOrCreate(long guildId) {
        GuildSettings existingSettings = findByGuildId(guildId);
        if (existingSettings != null) {
            return existingSettings;
        }

        Instant now = Instant.now();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO guild_settings (
                         guild_id,
                         greeting_enabled,
                         announcements_enabled,
                         voice_provider,
                         voice_id,
                         model_id,
                         created_at,
                         updated_at
                     ) VALUES (?, 1, 1, 'elevenlabs', ?, ?, ?, ?)
                     """)) {
            statement.setLong(1, guildId);
            statement.setString(2, emptyToNull(defaultVoiceId));
            statement.setString(3, defaultModelId);
            statement.setString(4, now.toString());
            statement.setString(5, now.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.error("Failed to create guild settings for {}", guildId, exception);
            throw new IllegalStateException("Failed to create guild settings.", exception);
        }

        GuildSettings settings = findByGuildId(guildId);
        if (settings == null) {
            throw new IllegalStateException("Guild settings were not created for guild " + guildId);
        }
        return settings;
    }

    public GuildSettings updateGreetingEnabled(long guildId, boolean enabled) {
        updateFlag(guildId, "greeting_enabled", enabled);
        return getOrCreate(guildId);
    }

    public GuildSettings updateAnnouncementsEnabled(long guildId, boolean enabled) {
        updateFlag(guildId, "announcements_enabled", enabled);
        return getOrCreate(guildId);
    }

    public GuildSettings updateLanguageCode(long guildId, String languageCode) {
        updateValue(guildId, "language_code", languageCode);
        return getOrCreate(guildId);
    }

    private GuildSettings findByGuildId(long guildId) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT guild_id,
                            greeting_enabled,
                            announcements_enabled,
                            language_code,
                            voice_provider,
                            voice_id,
                            model_id
                     FROM guild_settings
                     WHERE guild_id = ?
                     """)) {
            statement.setLong(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return new GuildSettings(
                        resultSet.getLong("guild_id"),
                        resultSet.getInt("greeting_enabled") == 1,
                        resultSet.getInt("announcements_enabled") == 1,
                        defaultLanguage(resultSet.getString("language_code")),
                        resultSet.getString("voice_provider"),
                        resultSet.getString("voice_id"),
                        resultSet.getString("model_id")
                );
            }
        } catch (SQLException exception) {
            LOGGER.error("Failed to load guild settings for {}", guildId, exception);
            throw new IllegalStateException("Failed to load guild settings.", exception);
        }
    }

    private void updateFlag(long guildId, String columnName, boolean enabled) {
        getOrCreate(guildId);
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE guild_settings "
                             + "SET " + columnName + " = ?, "
                             + "updated_at = ? "
                             + "WHERE guild_id = ?"
             )) {
            statement.setInt(1, enabled ? 1 : 0);
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, guildId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.error("Failed to update {} for {}", columnName, guildId, exception);
            throw new IllegalStateException("Failed to update guild settings.", exception);
        }
    }

    private void updateValue(long guildId, String columnName, String value) {
        getOrCreate(guildId);
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE guild_settings "
                             + "SET " + columnName + " = ?, "
                             + "updated_at = ? "
                             + "WHERE guild_id = ?"
             )) {
            statement.setString(1, value);
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, guildId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.error("Failed to update {} for {}", columnName, guildId, exception);
            throw new IllegalStateException("Failed to update guild settings.", exception);
        }
    }

    private String defaultLanguage(String value) {
        return value == null || value.isBlank() ? "en" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
