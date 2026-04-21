package com.virabot.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseManager.class);

    private final String jdbcUrl;

    public DatabaseManager(Path databasePath) {
        try {
            Path parent = databasePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create database directory.", exception);
        }

        this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        initializeSchema();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void initializeSchema() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_settings (
                        guild_id INTEGER PRIMARY KEY,
                        greeting_enabled INTEGER NOT NULL DEFAULT 1,
                        announcements_enabled INTEGER NOT NULL DEFAULT 1,
                        voice_provider TEXT NOT NULL DEFAULT 'elevenlabs',
                        voice_id TEXT,
                        model_id TEXT NOT NULL DEFAULT 'eleven_multilingual_v2',
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            addColumnIfMissing(statement,
                    "ALTER TABLE guild_settings ADD COLUMN language_code TEXT NOT NULL DEFAULT 'en'");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS play_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id INTEGER NOT NULL,
                        track_title TEXT NOT NULL,
                        track_uri TEXT,
                        played_at TEXT NOT NULL
                    )
                    """);
        } catch (SQLException exception) {
            LOGGER.error("Failed to initialize database schema", exception);
            throw new IllegalStateException("Failed to initialize database schema.", exception);
        }
    }

    private void addColumnIfMissing(Statement statement, String sql) throws SQLException {
        try {
            statement.execute(sql);
        } catch (SQLException exception) {
            if (!exception.getMessage().contains("duplicate column name")) {
                throw exception;
            }
        }
    }
}
