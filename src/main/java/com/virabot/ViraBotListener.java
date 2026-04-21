package com.virabot;

import com.virabot.music.MusicService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class ViraBotListener extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ViraBotListener.class);
    private final MusicService musicService = new MusicService();

    @Override
    public void onReady(ReadyEvent event) {
        LOGGER.info("Connected as {}", event.getJDA().getSelfUser().getAsTag());
        List<CommandData> commands = List.of(
                Commands.slash("ping", "Check if the bot is responding"),
                Commands.slash("join", "Join your current voice channel"),
                Commands.slash("play", "Play audio from a YouTube URL")
                        .addOptions(new OptionData(OptionType.STRING, "url", "A YouTube video URL", true)),
                Commands.slash("queue", "Show the current music queue"),
                Commands.slash("language", "Choose the spoken announcement language")
                        .addOptions(new OptionData(OptionType.STRING, "mode", "Voice language", true)
                                .addChoice("English", "en")
                                .addChoice("Japanese", "ja")),
                Commands.slash("greeting", "Enable or disable the voice greeting")
                        .addOptions(new OptionData(OptionType.BOOLEAN, "enabled", "Whether greeting is enabled", true)),
                Commands.slash("announcements", "Enable or disable spoken track announcements")
                        .addOptions(new OptionData(OptionType.BOOLEAN, "enabled", "Whether song announcements are enabled", true)),
                Commands.slash("pause", "Pause the current track"),
                Commands.slash("resume", "Resume the current track"),
                Commands.slash("skip", "Skip the current track"),
                Commands.slash("leave", "Disconnect from the current voice channel")
        );

        event.getJDA().updateCommands()
                .queue(
                        success -> LOGGER.info("Cleared stale global slash commands"),
                        error -> LOGGER.error("Failed to clear stale global slash commands", error)
                );

        event.getJDA().getGuilds().forEach(guild ->
                guild.updateCommands()
                        .addCommands(commands)
                        .queue(
                                success -> LOGGER.info("Registered slash commands for guild {}", guild.getName()),
                                error -> LOGGER.error("Failed to register slash commands for guild {}", guild.getName(), error)
                        )
        );
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "ping" -> event.reply("Pong!").setEphemeral(true).queue();
            case "join" -> {
                event.deferReply(true).queue();
                event.getHook()
                        .sendMessage(musicService.connectToMemberVoiceChannel(event))
                        .queue();
            }
            case "play" -> {
                String url = event.getOption("url", null, option -> option.getAsString().trim());
                if (url == null || url.isBlank()) {
                    event.reply("Provide a YouTube URL.").setEphemeral(true).queue();
                    return;
                }

                event.deferReply(true).queue();
                musicService.loadAndPlay(event, url);
            }
            case "queue" -> {
                if (event.getGuild() == null) {
                    event.reply("This command only works in a server.").setEphemeral(true).queue();
                    return;
                }

                event.replyEmbeds(musicService.buildQueueEmbed(event.getGuild()))
                        .setEphemeral(true)
                        .queue();
            }
            case "greeting" -> {
                boolean enabled = event.getOption("enabled", false, option -> option.getAsBoolean());
                event.reply(musicService.setGreetingEnabled(event, enabled))
                        .setEphemeral(true)
                        .queue();
            }
            case "language" -> {
                String languageCode = event.getOption("mode", "en", option -> option.getAsString());
                event.deferReply(true).queue();
                event.getHook()
                        .sendMessage(musicService.setLanguage(event, languageCode))
                        .queue();
            }
            case "announcements" -> {
                boolean enabled = event.getOption("enabled", false, option -> option.getAsBoolean());
                event.reply(musicService.setAnnouncementsEnabled(event, enabled))
                        .setEphemeral(true)
                        .queue();
            }
            case "pause" -> {
                event.reply(musicService.pause(event))
                        .setEphemeral(true)
                        .queue();
            }
            case "resume" -> {
                event.reply(musicService.resume(event))
                        .setEphemeral(true)
                        .queue();
            }
            case "skip" -> {
                event.deferReply(true).queue();
                event.getHook()
                        .sendMessage(musicService.skip(event))
                        .queue();
            }
            case "leave" -> {
                event.deferReply(true).queue();
                event.getHook()
                        .sendMessage(musicService.leave(event))
                        .queue();
            }
            default -> {
            }
        }
    }
}
