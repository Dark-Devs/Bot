package io.groovybot.bot.core.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import io.groovybot.bot.GroovyBot;
import lavalink.client.io.jda.JdaLavalink;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.hooks.SubscribeEvent;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
public class LavalinkManager {

    @Getter
    private static JdaLavalink lavalink;
    @Getter
    private AudioPlayerManager audioPlayerManager;
    private GroovyBot groovyBot;

    public LavalinkManager(GroovyBot groovyBot) {
        log.info("[Lavalink] Connecting to lavalink nodes");
        this.groovyBot = groovyBot;
        this.audioPlayerManager = new DefaultAudioPlayerManager();
        audioPlayerManager.registerSourceManager(new YoutubeAudioSourceManager());
        audioPlayerManager.registerSourceManager(new SoundCloudAudioSourceManager());
        audioPlayerManager.registerSourceManager(new HttpAudioSourceManager());
    }

    public void initialize() {
        lavalink = new JdaLavalink(
                groovyBot.getShardManager().getApplicationInfo().complete().getId(),
                groovyBot.getShardManager().getShardsTotal(),
                groovyBot.getShardManager()::getShardById
        );
        try (Connection connection = groovyBot.getPostgreSQL().getDataSource().getConnection()) {
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM lavalink_nodes");
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                lavalink.addNode(URI.create(rs.getString("uri")), rs.getString("password"));
        } catch (SQLException e) {
            log.error("[Lavalink] Error while loading lavalink");
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    private void onEvent(Event event) {
        if (lavalink != null)
            lavalink.onEvent(event);
    }

    public static int countPlayers() {
        if (lavalink == null)
            return 0;
        AtomicInteger playingPlayers = new AtomicInteger();
        lavalink.getNodes().forEach(
                node -> {
                    if (node.getStats() != null)
                        playingPlayers.addAndGet(node.getStats().getPlayingPlayers());
                }
        );
        return playingPlayers.get();
    }
}
