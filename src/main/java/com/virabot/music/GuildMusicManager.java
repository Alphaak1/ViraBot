package com.virabot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.virabot.persistence.GuildSettings;
import com.virabot.persistence.GuildSettingsRepository;
import com.virabot.persistence.PlayHistoryRepository;
import com.virabot.voice.SpeechFormatter;
import com.virabot.voice.SpeechService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GuildMusicManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildMusicManager.class);

    private final long guildId;
    private final AudioPlayerManager playerManager;
    private final AudioPlayer player;
    private final AudioPlayer announcementPlayer;
    private final TrackScheduler scheduler;
    private final AudioPlayerSendHandler sendHandler;
    private final GuildSettingsRepository guildSettingsRepository;
    private final PlayHistoryRepository playHistoryRepository;
    private final SpeechService speechService;

    private AudioTrack pendingMusicTrack;
    private Path pendingAnnouncementFile;
    private Runnable afterAnnouncementAction;

    public GuildMusicManager(
            long guildId,
            AudioPlayerManager playerManager,
            GuildSettingsRepository guildSettingsRepository,
            PlayHistoryRepository playHistoryRepository,
            SpeechService speechService
    ) {
        this.guildId = guildId;
        this.playerManager = playerManager;
        this.guildSettingsRepository = guildSettingsRepository;
        this.playHistoryRepository = playHistoryRepository;
        this.speechService = speechService;
        this.player = playerManager.createPlayer();
        this.announcementPlayer = playerManager.createPlayer();
        this.player.setVolume(20);
        this.scheduler = new TrackScheduler(this);
        this.sendHandler = new AudioPlayerSendHandler(announcementPlayer, player);
        this.player.addListener(scheduler);
        this.announcementPlayer.addListener(new AnnouncementListener());
    }

    public AudioPlayerSendHandler getSendHandler() {
        return sendHandler;
    }

    public AudioPlayer getPlayer() {
        return player;
    }

    public AudioPlayer getAnnouncementPlayer() {
        return announcementPlayer;
    }

    public TrackScheduler getScheduler() {
        return scheduler;
    }

    public synchronized boolean isAnnouncementPlaying() {
        return announcementPlayer.getPlayingTrack() != null;
    }

    public synchronized AudioTrack getPendingMusicTrack() {
        return pendingMusicTrack;
    }

    public synchronized boolean isPlaybackIdle() {
        return player.getPlayingTrack() == null
                && announcementPlayer.getPlayingTrack() == null
                && pendingMusicTrack == null;
    }

    public void queueTrack(AudioTrack track) {
        scheduler.queue(track);
    }

    public synchronized void beginTrack(AudioTrack track) {
        GuildSettings settings = guildSettingsRepository.getOrCreate(guildId);
        pendingMusicTrack = track;

        if (!settings.announcementsEnabled() || !speechService.isConfiguredFor(settings.languageCode())) {
            startPendingMusicTrack();
            return;
        }

        try {
            Path announcementPath = speechService.synthesize(
                    SpeechFormatter.buildNowPlaying(track.getInfo().title, settings.languageCode()),
                    settings
            );
            playAnnouncement(announcementPath);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.error("Failed to synthesize track announcement for guild {}", guildId, exception);
            startPendingMusicTrack();
        } catch (RuntimeException exception) {
            LOGGER.error("Track announcement failed unexpectedly for guild {}", guildId, exception);
            startPendingMusicTrack();
        }
    }

    public synchronized void playGreeting(String channelName) {
        GuildSettings settings = guildSettingsRepository.getOrCreate(guildId);
        if (!settings.greetingEnabled()
                || !speechService.isConfiguredFor(settings.languageCode())
                || !isPlaybackIdle()) {
            return;
        }

        try {
            Path greetingPath = speechService.synthesize(
                    SpeechFormatter.buildGreeting(channelName, settings.languageCode()),
                    settings
            );
            playAnnouncement(greetingPath);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.error("Failed to synthesize greeting for guild {}", guildId, exception);
        } catch (RuntimeException exception) {
            LOGGER.error("Greeting failed unexpectedly for guild {}", guildId, exception);
        }
    }

    public synchronized AudioTrack skipCurrentTrack() {
        GuildSettings settings = guildSettingsRepository.getOrCreate(guildId);
        AudioTrack nextTrack = scheduler.pollNextTrack();
        player.setPaused(false);
        player.stopTrack();

        if (!settings.announcementsEnabled() || !speechService.isConfiguredFor(settings.languageCode())) {
            if (nextTrack != null) {
                beginTrack(nextTrack);
            }
            return nextTrack;
        }

        try {
            if (nextTrack == null) {
                playAnnouncement(
                        speechService.synthesize(SpeechFormatter.buildSkip(settings.languageCode()), settings),
                        null
                );
            } else {
                pendingMusicTrack = nextTrack;
                playAnnouncement(
                        speechService.synthesize(
                                SpeechFormatter.buildSkipAndNowPlaying(nextTrack.getInfo().title, settings.languageCode()),
                                settings
                        ),
                        null
                );
            }
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.error("Failed to synthesize skip announcement for guild {}", guildId, exception);
            if (nextTrack != null) {
                beginTrack(nextTrack);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Skip announcement failed unexpectedly for guild {}", guildId, exception);
            if (nextTrack != null) {
                beginTrack(nextTrack);
            }
        }

        return nextTrack;
    }

    public synchronized void playFarewell(Runnable afterFarewellAction) {
        GuildSettings settings = guildSettingsRepository.getOrCreate(guildId);
        scheduler.clearQueue();
        pendingMusicTrack = null;
        player.setPaused(false);
        player.stopTrack();

        if (!speechService.isConfiguredFor(settings.languageCode())) {
            afterFarewellAction.run();
            return;
        }

        try {
            playAnnouncement(
                    speechService.synthesize(SpeechFormatter.buildFarewell(settings.languageCode()), settings),
                    afterFarewellAction
            );
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.error("Failed to synthesize farewell for guild {}", guildId, exception);
            afterFarewellAction.run();
        } catch (RuntimeException exception) {
            LOGGER.error("Farewell failed unexpectedly for guild {}", guildId, exception);
            afterFarewellAction.run();
        }
    }

    public void stop() {
        scheduler.clearQueue();
        pendingMusicTrack = null;
        afterAnnouncementAction = null;
        announcementPlayer.stopTrack();
        player.stopTrack();
        cleanupPendingAnnouncementFile();
    }

    private synchronized void startPendingMusicTrack() {
        AudioTrack track = pendingMusicTrack;
        pendingMusicTrack = null;
        if (track == null) {
            return;
        }

        player.startTrack(track, false);
        playHistoryRepository.recordPlay(guildId, track.getInfo().title, track.getInfo().uri);
    }

    private synchronized void playAnnouncement(Path announcementPath, Runnable afterAction) {
        pendingAnnouncementFile = announcementPath;
        afterAnnouncementAction = afterAction;
        playerManager.loadItemOrdered(this, announcementPath.toString(), new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                announcementPlayer.startTrack(track, false);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack track = playlist.getSelectedTrack();
                if (track == null && !playlist.getTracks().isEmpty()) {
                    track = playlist.getTracks().getFirst();
                }
                if (track == null) {
                    cleanupPendingAnnouncementFile();
                    startPendingMusicTrack();
                    runAfterAnnouncementAction();
                    return;
                }

                announcementPlayer.startTrack(track, false);
            }

            @Override
            public void noMatches() {
                cleanupPendingAnnouncementFile();
                startPendingMusicTrack();
                runAfterAnnouncementAction();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                LOGGER.error("Failed to load announcement audio for guild {}", guildId, exception);
                cleanupPendingAnnouncementFile();
                startPendingMusicTrack();
                runAfterAnnouncementAction();
            }
        });
    }

    private synchronized void playAnnouncement(Path announcementPath) {
        playAnnouncement(announcementPath, null);
    }

    private synchronized void cleanupPendingAnnouncementFile() {
        if (pendingAnnouncementFile == null) {
            return;
        }

        try {
            Files.deleteIfExists(pendingAnnouncementFile);
        } catch (IOException exception) {
            LOGGER.warn("Failed to delete temporary announcement file {}", pendingAnnouncementFile, exception);
        } finally {
            pendingAnnouncementFile = null;
        }
    }

    private synchronized void runAfterAnnouncementAction() {
        if (afterAnnouncementAction == null) {
            return;
        }

        Runnable action = afterAnnouncementAction;
        afterAnnouncementAction = null;
        try {
            action.run();
        } catch (RuntimeException exception) {
            LOGGER.error("Post-announcement action failed for guild {}", guildId, exception);
        }
    }

    private final class AnnouncementListener extends AudioEventAdapter {
        @Override
        public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
            cleanupPendingAnnouncementFile();
            if (endReason.mayStartNext) {
                startPendingMusicTrack();
            }
            runAfterAnnouncementAction();
        }

        @Override
        public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
            LOGGER.error("Announcement playback failed for guild {}", guildId, exception);
            cleanupPendingAnnouncementFile();
            startPendingMusicTrack();
            runAfterAnnouncementAction();
        }
    }
}
