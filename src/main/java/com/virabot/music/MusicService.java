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
import com.virabot.config.EnvConfig;
import com.virabot.persistence.DatabaseManager;
import com.virabot.persistence.GuildSettings;
import com.virabot.persistence.GuildSettingsRepository;
import com.virabot.persistence.PlayHistoryRepository;
import com.virabot.voice.SpeechService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MusicService {
    private static final int DEFAULT_VOLUME = 20;
    private static final Logger LOGGER = LoggerFactory.getLogger(MusicService.class);

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();
    private final GuildSettingsRepository guildSettingsRepository;
    private final PlayHistoryRepository playHistoryRepository;
    private final SpeechService speechService;

    public MusicService() {
        this.playerManager = new DefaultAudioPlayerManager();
        Path databasePath = resolveDatabasePath();
        Path ttsTempDirectory = resolveTtsDirectory(databasePath);
        String englishVoiceId = firstPresent(EnvConfig.get("ELEVENLABS_ENGLISH_VOICE_ID"), EnvConfig.get("ELEVENLABS_VOICE_ID"));
        String japaneseVoiceId = EnvConfig.get("ELEVENLABS_JAPANESE_VOICE_ID");
        String defaultModelId = defaultValue(EnvConfig.get("ELEVENLABS_MODEL_ID"), "eleven_multilingual_v2");
        DatabaseManager databaseManager = new DatabaseManager(databasePath);
        this.guildSettingsRepository = new GuildSettingsRepository(databaseManager, englishVoiceId, defaultModelId);
        this.playHistoryRepository = new PlayHistoryRepository(databaseManager);
        this.speechService = new SpeechService(
                EnvConfig.get("ELEVENLABS_API_KEY"),
                englishVoiceId,
                japaneseVoiceId,
                defaultModelId,
                ttsTempDirectory
        );

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
        musicManager.playGreeting(channel.getName());
        return "Joined **" + channel.getName() + "**.";
    }

    public void loadAndPlay(SlashCommandInteractionEvent event, String trackUrl) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.getHook().sendMessage("This command only works in a server.").queue();
            return;
        }

        AudioChannel channel = getRequesterChannel(event);
        if (channel == null) {
            event.getHook().sendMessage("Join a voice channel first.").queue();
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
                musicManager.queueTrack(track);
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

                musicManager.queueTrack(track);
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

        GuildMusicManager musicManager = getExistingGuildMusicManager(guild);
        if (musicManager == null || guild.getAudioManager().getConnectedChannel() == null) {
            return "I am not connected to a voice channel.";
        }

        musicManager.playFarewell(() -> {
            guild.getAudioManager().closeAudioConnection();
            musicManager.stop();
            musicManagers.remove(guild.getIdLong());
        });
        return "Leaving voice after a final message.";
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
        AudioTrack nextTrack = musicManager.skipCurrentTrack();
        if (nextTrack == null) {
            return "Skipped **" + skippedTitle + "**. The queue is now empty.";
        }

        return "Skipped **" + skippedTitle + "**. Preparing **" + nextTrack.getInfo().title + "**.";
    }

    public MessageEmbed buildQueueEmbed(Guild guild) {
        GuildMusicManager musicManager = getExistingGuildMusicManager(guild);
        if (musicManager == null) {
            GuildSettings settings = guildSettingsRepository.getOrCreate(guild.getIdLong());
            return new EmbedBuilder()
                    .setTitle("ViraBot Queue")
                    .setColor(new Color(46, 204, 113))
                    .setDescription("Tracks in playback order for **" + guild.getName() + "**")
                    .addField("Status", "Idle", true)
                    .addField("Volume", DEFAULT_VOLUME + "%", true)
                    .addField("Queued", "0", true)
                    .addField("Language", describeLanguage(settings.languageCode()), true)
                    .addField("Greeting", settings.greetingEnabled() ? "On" : "Off", true)
                    .addField("Announcements", settings.announcementsEnabled() ? "On" : "Off", true)
                    .addField("Now Playing", "Nothing is currently playing.", false)
                    .addField("Up Next", "No tracks queued.", false)
                    .build();
        }

        GuildSettings settings = guildSettingsRepository.getOrCreate(guild.getIdLong());
        AudioPlayer player = musicManager.getPlayer();
        AudioTrack currentTrack = player.getPlayingTrack();
        AudioTrack pendingTrack = musicManager.getPendingMusicTrack();
        List<AudioTrack> queuedTracks = musicManager.getScheduler().getQueuedTracksSnapshot();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("ViraBot Queue")
                .setColor(new Color(46, 204, 113))
                .setDescription("Tracks in playback order for **" + guild.getName() + "**")
                .addField("Status", resolveStatus(musicManager, player, currentTrack), true)
                .addField("Volume", player.getVolume() + "%", true)
                .addField("Queued", String.valueOf(queuedTracks.size()), true)
                .addField("Language", describeLanguage(settings.languageCode()), true)
                .addField("Greeting", settings.greetingEnabled() ? "On" : "Off", true)
                .addField("Announcements", settings.announcementsEnabled() ? "On" : "Off", true);

        if (currentTrack == null && pendingTrack == null) {
            embed.addField("Now Playing", "Nothing is currently playing.", false);
        } else if (currentTrack == null) {
            embed.addField("Now Playing", "Preparing **" + pendingTrack.getInfo().title + "**.", false);
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
        return musicManagers.computeIfAbsent(guild.getIdLong(), id -> {
            guildSettingsRepository.getOrCreate(id);
            return new GuildMusicManager(id, playerManager, guildSettingsRepository, playHistoryRepository, speechService);
        });
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

    public String setGreetingEnabled(SlashCommandInteractionEvent event, boolean enabled) {
        Guild guild = event.getGuild();
        if (guild == null) {
            return "This command only works in a server.";
        }

        guildSettingsRepository.updateGreetingEnabled(guild.getIdLong(), enabled);
        return enabled
                ? "Join greeting is now enabled."
                : "Join greeting is now disabled.";
    }

    public String setAnnouncementsEnabled(SlashCommandInteractionEvent event, boolean enabled) {
        Guild guild = event.getGuild();
        if (guild == null) {
            return "This command only works in a server.";
        }

        guildSettingsRepository.updateAnnouncementsEnabled(guild.getIdLong(), enabled);
        return enabled
                ? "Track announcements are now enabled."
                : "Track announcements are now disabled.";
    }

    public String setLanguage(SlashCommandInteractionEvent event, String languageCode) {
        Guild guild = event.getGuild();
        if (guild == null) {
            return "This command only works in a server.";
        }

        String normalizedLanguage = normalizeLanguage(languageCode);
        guildSettingsRepository.updateLanguageCode(guild.getIdLong(), normalizedLanguage);
        GuildMusicManager musicManager = getExistingGuildMusicManager(guild);
        var connectedChannel = guild.getAudioManager().getConnectedChannel();
        if (musicManager != null && connectedChannel != null) {
            musicManager.playGreeting(connectedChannel.getName());
            return "Voice language is now set to **" + describeLanguage(normalizedLanguage)
                    + "**. Replaying the greeting in the current channel.";
        }

        return "Voice language is now set to **" + describeLanguage(normalizedLanguage) + "**.";
    }

    private String resolveStatus(GuildMusicManager musicManager, AudioPlayer player, AudioTrack currentTrack) {
        if (musicManager.isAnnouncementPlaying()) {
            return "Announcing";
        }
        if (currentTrack == null) {
            return "Idle";
        }
        return player.isPaused() ? "Paused" : "Playing";
    }

    private Path resolveDatabasePath() {
        String configuredPath = EnvConfig.get("VIRABOT_DB_PATH");
        if (configuredPath == null || configuredPath.isBlank()) {
            return Path.of("data", "virabot.db");
        }
        return Path.of(configuredPath);
    }

    private Path resolveTtsDirectory(Path databasePath) {
        Path parent = databasePath.toAbsolutePath().getParent();
        if (parent == null) {
            LOGGER.warn("Database path {} has no parent directory; using local tts-temp directory", databasePath);
            return Path.of("tts-temp");
        }
        return parent.resolve("tts-temp");
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String firstPresent(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String normalizeLanguage(String languageCode) {
        return "ja".equalsIgnoreCase(languageCode) ? "ja" : "en";
    }

    private String describeLanguage(String languageCode) {
        return "ja".equalsIgnoreCase(languageCode) ? "Japanese" : "English";
    }
}
