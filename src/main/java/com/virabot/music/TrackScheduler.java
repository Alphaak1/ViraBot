package com.virabot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class TrackScheduler extends AudioEventAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackScheduler.class);

    private final AudioPlayer player;
    private final Queue<AudioTrack> queue = new ConcurrentLinkedQueue<>();

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
    }

    public void queue(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        }
    }

    public void clearQueue() {
        queue.clear();
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            AudioTrack nextTrack = queue.poll();
            player.startTrack(nextTrack, false);
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        LOGGER.error("Track playback failed for {}", track.getInfo().uri, exception);
        AudioTrack nextTrack = queue.poll();
        if (nextTrack != null) {
            player.startTrack(nextTrack, false);
        }
    }
}

