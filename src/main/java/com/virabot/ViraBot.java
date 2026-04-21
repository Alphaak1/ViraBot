package com.virabot;

import com.virabot.config.EnvConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import moe.kyokobot.libdave.DaveFactory;
import moe.kyokobot.libdave.NativeDaveFactory;
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ViraBot {
    private static final Logger LOGGER = LoggerFactory.getLogger(ViraBot.class);

    private ViraBot() {
    }

    public static void main(String[] args) {
        String token = EnvConfig.get("DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("DISCORD_TOKEN is not set.");
        }

        DaveFactory daveFactory = new NativeDaveFactory();
        AudioModuleConfig audioModuleConfig = new AudioModuleConfig()
                .withDaveSessionFactory(new LDJDADaveSessionFactory(daveFactory));

        JDA jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_VOICE_STATES)
                .enableCache(CacheFlag.VOICE_STATE)
                .setMemberCachePolicy(MemberCachePolicy.VOICE)
                .setAudioModuleConfig(audioModuleConfig)
                .addEventListeners(new ViraBotListener())
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down Discord bot");
            jda.shutdown();
        }));
    }
}
