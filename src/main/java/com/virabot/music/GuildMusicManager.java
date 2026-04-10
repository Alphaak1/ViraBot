package com.virabot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

public final class GuildMusicManager {
    private final AudioPlayer player;
    private final TrackScheduler scheduler;
    private final AudioPlayerSendHandler sendHandler;

    public GuildMusicManager(AudioPlayerManager playerManager) {
        this.player = playerManager.createPlayer();
        this.scheduler = new TrackScheduler(player);
        this.sendHandler = new AudioPlayerSendHandler(player);
        this.player.addListener(scheduler);
    }

    public AudioPlayerSendHandler getSendHandler() {
        return sendHandler;
    }

    public TrackScheduler getScheduler() {
        return scheduler;
    }

    public void stop() {
        scheduler.clearQueue();
        player.stopTrack();
    }
}

