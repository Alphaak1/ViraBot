# ViraBot

Simple Discord bot in Java using JDA.

## What is included

- Java 21 Maven project
- Bot startup from `DISCORD_TOKEN`
- Slash command registration on startup
- `/ping` command that replies with `Pong!`
- Basic music playback from a YouTube URL

## Prerequisites

- Java 21
- Maven 3.9+
- A Discord bot token from the Discord Developer Portal

## Setup

1. Put `DISCORD_TOKEN` in the repo-root `.env` file, or set it as an environment variable.
2. Install Maven if it is not already available on your machine.
3. If you want spoken greetings and spoken track announcements, also put these in `.env`:
   - `ELEVENLABS_API_KEY`
   - `ELEVENLABS_ENGLISH_VOICE_ID`
   - `ELEVENLABS_JAPANESE_VOICE_ID`
   - Optional: `ELEVENLABS_VOICE_ID` as a backward-compatible fallback for English
   - Optional: `ELEVENLABS_MODEL_ID` (defaults to `eleven_multilingual_v2`)
4. Optional: set `VIRABOT_DB_PATH` if you do not want the SQLite database stored at `data/virabot.db`.

## Run

```bash
mvn compile
mvn exec:java
```

The app now loads `.env` automatically on startup, so you do not need to export variables manually if they are already in that file.

## Bot behavior

- Registers guild slash commands when the bot becomes ready and clears stale global commands
- Replies to `/ping` with `Pong!`
- `/join` makes the bot join your current voice channel and optionally speak a greeting
- `/play` accepts a YouTube URL and queues that track for playback
- `/queue` shows the current queue plus greeting / announcement settings
- `/language` switches the spoken announcement language between English and Japanese and persists the setting in SQLite
- `/greeting` toggles the voice greeting for that guild and persists the setting in SQLite
- `/announcements` toggles spoken "now playing" announcements for that guild and persists the setting in SQLite
- `/leave` disconnects the bot and clears the queue

## Notes

- This repository does not commit secrets; `.env` is ignored.
- The Java code reads config from real environment variables first, then falls back to the repo-root `.env` file automatically.
- If you invite the bot to a server, make sure the application has the `applications.commands` and `bot` scopes.
- For music playback, give the bot `View Channels`, `Connect`, `Speak`, and `Send Messages` permissions.
- Playback history is written to SQLite at `data/virabot.db` by default.
