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
                Commands.slash("leave", "Disconnect from the current voice channel")
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
            case "ping" -> event.reply("Pong!").queue();
            case "join" -> event.reply(musicService.connectToMemberVoiceChannel(event))
                    .setEphemeral(true)
                    .queue();
            case "play" -> {
                String url = event.getOption("url", null, option -> option.getAsString().trim());
                if (url == null || url.isBlank()) {
                    event.reply("Provide a YouTube URL.").setEphemeral(true).queue();
                    return;
                }

                event.deferReply().queue();
                musicService.loadAndPlay(event, url);
            }
            case "leave" -> {
                if (event.getGuild() == null) {
                    event.reply("This command only works in a server.").setEphemeral(true).queue();
                    return;
                }

                event.reply(musicService.leave(event.getGuild()))
                        .setEphemeral(true)
                        .queue();
            }
            default -> {
            }
        }
    }
}
