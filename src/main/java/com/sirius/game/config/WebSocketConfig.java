package com.sirius.game.config;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Props;
import com.google.protobuf.InvalidProtocolBufferException;
import com.sirius.game.actor.RootActor;
import com.sirius.game.actor.PlayerActor;
import com.sirius.game.proto.Message;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Configuration
public class WebSocketConfig {

    @Value("${vertx.websocket.port:8081}")
    private int websocketPort;

    private Vertx vertx;
    private HttpServer httpServer;
    private ActorSystem<Object> actorSystem;

    private final Map<String, ActorRef<Object>> players = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("Initializing Akka Actor System");
        try {
            // 使用简化的非集群配置
            actorSystem = ActorSystem.create(RootActor.create(players), "GameSystem");
            log.info("Akka Actor System initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Akka Actor System", e);
            throw new RuntimeException("Akka initialization failed", e);
        }

        log.info("Initializing Vertx WebSocket server on port {}", websocketPort);

        vertx = Vertx.vertx();
        httpServer = vertx.createHttpServer();

        httpServer.webSocketHandler(webSocket -> {
                    String playerId = UUID.randomUUID().toString();
                    log.info("New WebSocket connection from {} with playerId {}", webSocket.remoteAddress(), playerId);

                    ActorRef<Object> actorRef;
                    if (players.containsKey(playerId)) {
                        actorRef = players.get(playerId);
                    } else {
                        actorRef = actorSystem.systemActorOf(PlayerActor.create(playerId, webSocket), "player-" + playerId, Props.empty());
                        players.put(playerId, actorRef);
                    }

                    // 处理二进制消息
                    webSocket.handler(buffer -> {
                        try {
                            Message message = Message.parseFrom(buffer.getBytes());
                            actorRef.tell(message);
                        } catch (InvalidProtocolBufferException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    // 处理关闭
                    webSocket.closeHandler(v -> {
                        log.info("WebSocket closed: {}", playerId);
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