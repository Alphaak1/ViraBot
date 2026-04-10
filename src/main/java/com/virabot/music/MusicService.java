package com.virabot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
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
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MusicService {
    private static final int DEFAULT_VOLUME = 20;

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
        musicManager.getPlayer().setVolume(DEFAULT_VOLUME);
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
        musicManager.getPlayer().setVolume(DEFAULT_VOLUME);
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

    public String leave(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            return "This command only works in a server.";
        }

        GuildMusicManager musicManager = getGuildMusicManager(guild);
        guild.getAudioManager().closeAudioConnection();
        musicManager.stop();
        musicManagers.remove(guild.getIdLong());
        return "Disconnected from voice.";
    }

    public String pause(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            return "This command only works in a server.";
        }

        GuildMusicManager musicManager = getExistingGuildMusicManager(guild);
        if (musicManager == null) {
            return "Nothing is playing right now.";
        }

        AudioPlayer player = musicManager.getPlayer();
        AudioTrack currentTrack = player.getPlayingTrack();
        if (currentTrack == null) {
            return "Nothing is playing right now.";
        }

        if (player.isPaused()) {
            return "Playback is already paused.";
        }

        player.setPaused(true);
        return "Paused **" + currentTrack.getInfo().title + "**.";
    }

    public String resume(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            return "This command only works in a server.";
        }

        GuildMusicManager musicManager = getExistingGuildMusicManager(guild);
        if (musicManager == null) {
            return "Nothing is playing right now.";
        }

        AudioPlayer player = musicManager.getPlayer();
        AudioTrack currentTrack = player.getPlayingTrack();
        if (currentTrack == null) {
            return "Nothing is playing right now.";
        }

        if (!player.isPaused()) {
            return "Playback is already running.";
        }

        player.setPaused(false);
        return "Resumed **" + currentTrack.getInfo().title + "**.";
    }

    public String skip(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            return "This command only works in a server.";
        }

        GuildMusicManager musicManager = getExistingGuildMusicManager(guild);
        if (musicManager == null) {
            return "Nothing is playing right now.";
        }

        AudioPlayer player = musicManager.getPlayer();
        AudioTrack currentTrack = player.getPlayingTrack();
        if (currentTrack == null) {
            return "Nothing is playing right now.";
        }

        String skippedTitle = currentTrack.getInfo().title;
        AudioTrack nextTrack = musicManager.getScheduler().nextTrack();
        player.setPaused(false);
        if (nextTrack == null) {
            return "Skipped **" + skippedTitle + "**. The queue is now empty.";
        }

        return "Skipped **" + skippedTitle + "**. Now playing **" + nextTrack.getInfo().title + "**.";
    }

    public MessageEmbed buildQueueEmbed(Guild guild) {
        GuildMusicManager musicManager = getExistingGuildMusicManager(guild);
        if (musicManager == null) {
            return new EmbedBuilder()
                    .setTitle("ViraBot Queue")
                    .setColor(new Color(46, 204, 113))
                    .setDescription("Tracks in playback order for **" + guild.getName() + "**")
                    .addField("Status", "Idle", true)
                    .addField("Volume", DEFAULT_VOLUME + "%", true)
                    .addField("Queued", "0", true)
                    .addField("Now Playing", "Nothing is currently playing.", false)
                    .addField("Up Next", "No tracks queued.", false)
                    .build();
        }

        AudioPlayer player = musicManager.getPlayer();
        AudioTrack currentTrack = player.getPlayingTrack();
        List<AudioTrack> queuedTracks = musicManager.getScheduler().getQueuedTracksSnapshot();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("ViraBot Queue")
                .setColor(new Color(46, 204, 113))
                .setDescription("Tracks in playback order for **" + guild.getName() + "**")
                .addField("Status", currentTrack == null ? "Idle" : (player.isPaused() ? "Paused" : "Playing"), true)
                .addField("Volume", player.getVolume() + "%", true)
                .addField("Queued", String.valueOf(queuedTracks.size()), true);

        if (currentTrack == null) {
            embed.addField("Now Playing", "Nothing is currently playing.", false);
        } else {
            embed.addField("Now Playing", "1. " + currentTrack.getInfo().title, false);
        }

        if (queuedTracks.isEmpty()) {
            embed.addField("Up Next", "No tracks queued.", false);
        } else {
            StringBuilder queueText = new StringBuilder();
            for (int i = 0; i < queuedTracks.size() && i < 10; i++) {
                queueText.append(i + 2)
                        .append(". ")
                        .append(queuedTracks.get(i).getInfo().title)
                        .append("\n");
            }
            if (queuedTracks.size() > 10) {
                queueText.append("...and ").append(queuedTracks.size() - 10).append(" more");
            }
            embed.addField("Up Next", queueText.toString(), false);
        }

        return embed.build();
    }

    private GuildMusicManager getGuildMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(), id -> new GuildMusicManager(playerManager));
    }

    private GuildMusicManager getExistingGuildMusicManager(Guild guild) {
        return musicManagers.get(guild.getIdLong());
    }

    private AudioChannel getRequesterChannel(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || member.getVoiceState() == null) {
            return null;
        }

        return member.getVoiceState().getChannel();
    }
}
