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

1. Copy `.env.example` to `.env` or set an environment variable named `DISCORD_TOKEN`.
2. Put your bot token into `DISCORD_TOKEN`.
3. Install Maven if it is not already available on your machine.

## Run

```bash
mvn compile
mvn exec:java
```

On Windows `cmd.exe`, you can also set the token for the current shell and run:

```bat
set DISCORD_TOKEN=your-token-here
mvn exec:java
```

## Bot behavior

- Registers the `/ping`, `/join`, `/play`, and `/leave` slash commands when the bot becomes ready
- Replies to `/ping` with `Pong!`
- `/join` makes the bot join your current voice channel
- `/play` accepts a YouTube URL and queues that track for playback
- `/leave` disconnects the bot and clears the queue

## Notes

- This repository does not commit secrets; `.env` is ignored.
- The Java code reads `DISCORD_TOKEN` from the environment directly; `.env` is only an example file.
- If you invite the bot to a server, make sure the application has the `applications.commands` and `bot` scopes.
- For music playback, give the bot `View Channels`, `Connect`, `Speak`, and `Send Messages` permissions.
