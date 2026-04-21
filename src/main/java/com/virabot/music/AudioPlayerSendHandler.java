package com.virabot.music;

import java.nio.ByteBuffer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;

import net.dv8tion.jda.api.audio.AudioSendHandler;

public final class AudioPlayerSendHandler implements AudioSendHandler {
    private final AudioPlayer announcementPlayer;
    private final AudioPlayer musicPlayer;
    private AudioFrame lastFrame;

    public AudioPlayerSendHandler(AudioPlayer announcementPlayer, AudioPlayer musicPlayer) {
        this.announcementPlayer = announcementPlayer;
        this.musicPlayer = musicPlayer;
    }

    @Override
    public boolean canProvide() {
        lastFrame = announcementPlayer.provide();
        if (lastFrame != null) {
            return true;
        }

        lastFrame = musicPlayer.provide();
        return lastFrame != null;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        return ByteBuffer.wrap(lastFrame.getData());
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
