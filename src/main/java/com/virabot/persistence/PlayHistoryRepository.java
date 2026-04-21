package com.virabot.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;

public final class PlayHistoryRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayHistoryRepository.class);

    private final DatabaseManager databaseManager;

    public PlayHistoryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void recordPlay(long guildId, String trackTitle, String trackUri) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO play_history (guild_id, track_title, track_uri, played_at)
                     VALUES (?, ?, ?, ?)
                     """)) {
            statement.setLong(1, guildId);
            statement.setString(2, trackTitle);
            statement.setString(3, trackUri);
            statement.setString(4, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.error("Failed to record play history for guild {}", guildId, exception);
        }
    }
}
