package com.sirius.game.config;

import akka.actor.PoisonPill;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Props;
import akka.actor.typed.javadsl.Behaviors;
import com.sirius.game.actor.PlayerActor;
import com.sirius.game.actor.RootActor;
import com.sirius.game.proto.CSLogin;
import com.sirius.game.proto.GameMessage;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Configuration
public class WebSocketConfig {

    @Value("${vertx.websocket.port:8081}")
    private int websocketPort;

    @Value("${akka.cluster.port:10001}")
    private int akkaClusterPort;

    private Vertx vertx;
    private HttpServer httpServer;
    private ActorSystem<Object> actorSystem;

    private final Map<String, ActorRef<Object>> players = new ConcurrentHashMap<>();

    @SneakyThrows
    @PostConstruct
    public void init() {
        log.info("Initializing Akka Actor System with cluster port: {}", akkaClusterPort);
        
        // 加载配置并设置集群端口
        Config config = ConfigFactory.load();
        Config customConfig = ConfigFactory.parseString(
            "akka.remote.artery.canonical.port = " + akkaClusterPort + "\n" +
            "akka.cluster.roles = [\"game-node\"]"
        );
        Config finalConfig = customConfig.withFallback(config);
        
        actorSystem = ActorSystem.create(RootActor.create(players), "GameSystem", finalConfig);

        log.info("Initializing Vertx WebSocket server on port {}", websocketPort);
        vertx = Vertx.vertx();
        httpServer = vertx.createHttpServer();

        httpServer.webSocketHandler(webSocket -> {
                    String[] playerIds = new String[1];
                    ActorRef<Object>[] actorRefs = new ActorRef[1];

                    webSocket.handler(buffer -> dispatch(webSocket, playerIds, actorRefs, buffer));

                    webSocket.closeHandler(v -> {
                        if (actorRefs[0] != null) {
                            actorRefs[0].tell(PoisonPill.getInstance());
                        }
                        players.remove(playerIds[0]);
                    });
                })
                .listen(websocketPort, result -> {
                    if (result.succeeded()) {
                        log.info("Vertx WebSocket server started successfully on port {}", websocketPort);
                    } else {
                        log.error("Failed to start Vertx WebSocket server", result.cause());
                    }
                });
    }

    @SneakyThrows
    private void dispatch(ServerWebSocket webSocket, String[] playerIds, ActorRef<Object>[] actorRefs, Buffer buffer) {
        GameMessage message = GameMessage.parseFrom(buffer.getBytes());
        switch (message.getType()) {
            case CS_LOGIN:
                CSLogin csLogin = message.getCsLogin();
                playerIds[0] = csLogin.getUsername();
                log.info("New WebSocket connection from {} with playerId {}", webSocket.remoteAddress(), playerIds[0]);
                if (players.containsKey(playerIds[0])) {
                    actorRefs[0] = players.get(playerIds[0]);
                } else {
                    actorRefs[0] = actorSystem.systemActorOf(PlayerActor.create(playerIds[0], webSocket), "player-" + playerIds[0], Props.empty());
                    players.put(playerIds[0], actorRefs[0]);
                }
                actorRefs[0].tell(csLogin);
                break;
            case CS_SEND_MESSAGE:
                actorRefs[0].tell(message.getCsSendMessage());
                break;
        }
    }

    @PreDestroy
    public void destroy() {
        if (httpServer != null) {
            httpServer.close();
        }
        if (vertx != null) {
            vertx.close();
        }
        if (actorSystem != null) {
            actorSystem.terminate();
        }
        log.info("Vertx WebSocket server and Akka system stopped");
    }

    @Bean
    public Vertx vertx() {
        return vertx;
    }

    @Bean
    public HttpServer httpServer() {
        return httpServer;
    }

    @Bean
    public ActorSystem<Object> actorSystem() {
        return actorSystem;
    }
}