package io.groovybot.bot.io;

import com.zaxxer.hikari.HikariDataSource;
import io.groovybot.bot.GroovyBot;
import io.groovybot.bot.core.audio.LavalinkManager;
import lombok.extern.log4j.Log4j2;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


@Log4j2
public class WebsocketConnection extends WebSocketClient {

    private HikariDataSource dataSource;

    public WebsocketConnection() throws URISyntaxException {
        super(new URI(String.format("%s:%s", GroovyBot.getInstance().getConfig().getJSONObject("websocket").getString("host"), GroovyBot.getInstance().getConfig().getJSONObject("websocket").getInt("port"))));
        log.info("[Websocket] Connecting to websocket");
        this.connect();
        this.dataSource = GroovyBot.getInstance().getPostgreSQL().getDataSource();
    }

    public static JSONObject parseStats(int playing, int guilds, int users) {
        JSONObject object = new JSONObject();
        object.put("playing", playing);
        object.put("guilds", guilds);
        object.put("users", users);

        return object;
    }

    public static JSONObject parseMessage(String client, String type, JSONObject data) {
        JSONObject object = new JSONObject();
        object.put("client", client);
        object.put("type", type);
        object.put("data", data);

        return object;
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        log.info("[Websocket] WebsocketConnection opened!");
        authorize();
        this.send(WebsocketConnection.parseMessage("bot", "poststats", WebsocketConnection.parseStats(LavalinkManager.countPlayers(), GroovyBot.getInstance().getShardManager().getGuilds().size(), GroovyBot.getInstance().getShardManager().getUsers().size())).toString());
    }

    @Override
    public void onMessage(String message) {
        JSONObject object = new JSONObject(message);

        if (!object.has("type") || !object.has("data"))
            return;

        if (object.get("type").equals("error"))
            log.error("[Websocket] An error occurred! " + object.getJSONObject("data").getString("text"));

        if (object.get("type").equals("forbidden"))
            authorize();

        if (object.get("type").equals("botgetstats"))
            this.send(WebsocketConnection.parseMessage("bot", "poststats", WebsocketConnection.parseStats(LavalinkManager.countPlayers(), GroovyBot.getInstance().getShardManager().getGuilds().size(), GroovyBot.getInstance().getShardManager().getUsers().size())).toString());
    }

    @Override
    public void onClose(int i, String s, boolean b) {
        log.info("[Websocket] WebsocketConnection closed! Trying to reconnect in 5 seconds");
        try {
            Thread.sleep(5000L);
            new Thread(this::reconnect, "WebsocketThread").start();
        } catch (InterruptedException e) {
            log.error("[Websocket] Error while reconnecting");
        }
    }

    @Override
    public void onError(Exception e) {
        log.error("[Websocket] Error in WebsocketConnection!", e);
    }

    public void authorize() {
        String token = null;

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement getToken = connection.prepareStatement("SELECT * FROM websocket");
            ResultSet rs = getToken.executeQuery();
            while (rs.next()) {
                token = rs.getString("token");
            }
        } catch (SQLException e) {
            log.error("[Websocket] Error while authorizing!", e);
        }

        this.send(parseMessage("bot", "authorization", new JSONObject().put("token", token)).toString());
    }
}
