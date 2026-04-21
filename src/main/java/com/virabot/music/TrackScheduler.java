package com.virabot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class TrackScheduler extends AudioEventAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackScheduler.class);

    private final GuildMusicManager guildMusicManager;
    private final Queue<AudioTrack> queue = new ConcurrentLinkedQueue<>();

    public TrackScheduler(GuildMusicManager guildMusicManager) {
        this.guildMusicManager = guildMusicManager;
    }

    public void queue(AudioTrack track) {
        if (guildMusicManager.isPlaybackIdle()) {
            guildMusicManager.beginTrack(track);
            return;
        }

        queue.offer(track);
    }

    public void clearQueue() {
        queue.clear();
    }

    public AudioTrack nextTrack() {
        AudioTrack nextTrack = queue.poll();
        if (nextTrack != null) {
            guildMusicManager.beginTrack(nextTrack);
        }
        return nextTrack;
    }

    public AudioTrack peekNextTrack() {
        return queue.peek();
    }

    public AudioTrack pollNextTrack() {
        return queue.poll();
    }

    public List<AudioTrack> getQueuedTracksSnapshot() {
        return new ArrayList<>(queue);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            nextTrack();
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        LOGGER.error("Track playback failed for {}", track.getInfo().uri, exception);
        nextTrack();
    }
}
