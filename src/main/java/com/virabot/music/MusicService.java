package com.virabot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.MWeb;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbedded;
import dev.lavalink.youtube.clients.skeleton.Client;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MusicService {
    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();

    public MusicService() {
        this.playerManager = new DefaultAudioPlayerManager();

        YoutubeAudioSourceManager youtubeSourceManager = new YoutubeAudioSourceManager(
                false,
                new Client[]{new MWeb(), new WebEmbedded(), new AndroidVr(), new Web()}
        );
        playerManager.registerSourceManager(youtubeSourceManager);
        AudioSourceManagers.registerRemoteSources(
                playerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class
        );
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    public String connectToMemberVoiceChannel(SlashCommandInteractionEvent event) {
        AudioChannel channel = getRequesterChannel(event);
        if (channel == null) {
            return "Join a voice channel first.";
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            return "This command only works in a server.";
        }

        GuildMusicManager musicManager = getGuildMusicManager(guild);
        AudioManager audioManager = guild.getAudioManager();
        audioManager.setSelfDeafened(true);
        audioManager.setSendingHandler(musicManager.getSendHandler());
        audioManager.openAudioConnection(channel);
        return "Joined **" + channel.getName() + "**.";
    }

    public void loadAndPlay(SlashCommandInteractionEvent event, String trackUrl) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command only works in a server.").setEphemeral(true).queue();
            return;
        }

        AudioChannel channel = getRequesterChannel(event);
        if (channel == null) {
            event.reply("Join a voice channel first.").setEphemeral(true).queue();
            return;
        }

        GuildMusicManager musicManager = getGuildMusicManager(guild);
        AudioManager audioManager = guild.getAudioManager();
        audioManager.setSelfDeafened(true);
        audioManager.setSendingHandler(musicManager.getSendHandler());
        audioManager.openAudioConnection(channel);

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                musicManager.getScheduler().queue(track);
                event.getHook().sendMessage("Queued **" + track.getInfo().title + "**.").queue();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack selectedTrack = playlist.getSelectedTrack();
                AudioTrack track = selectedTrack != null ? selectedTrack : playlist.getTracks().stream().findFirst().orElse(null);
                if (track == null) {
                    event.getHook().sendMessage("That YouTube link did not return a playable track.").queue();
                    return;
                }

                musicManager.getScheduler().queue(track);
                event.getHook().sendMessage("Queued **" + track.getInfo().title + "**.").queue();
            }

            @Override
            public void noMatches() {
                event.getHook().sendMessage("No playable track was found for that URL.").queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                event.getHook().sendMessage("Failed to load that track: " + exception.getMessage()).queue();
            }
        });
    }

    public String leave(Guild guild) {
        GuildMusicManager musicManager = getGuildMusicManager(guild);
        guild.getAudioManager().closeAudioConnection();
        musicManager.stop();
        return "Disconnected from voice.";
    }

    private GuildMusicManager getGuildMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(), id -> new GuildMusicManager(playerManager));
    }

    private AudioChannel getRequesterChannel(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || member.getVoiceState() == null) {
            return null;
        }

        return member.getVoiceState().getChannel();
    }
}
