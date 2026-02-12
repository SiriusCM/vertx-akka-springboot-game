package com.sirius.game.config;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import com.sirius.game.actor.PlayerActor;
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

    public static final Map<String, EntityRef<Object>> playerMap = new ConcurrentHashMap<>();
    public static final Map<String, ServerWebSocket> webSocketMap = new ConcurrentHashMap<>();

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

        actorSystem = ActorSystem.create(Behaviors.empty(), "GameSystem", finalConfig);

        // 初始化集群分片
        PlayerActor.initSharding(actorSystem);

        log.info("Initializing Vertx WebSocket server on port {}", websocketPort);
        vertx = Vertx.vertx();
        httpServer = vertx.createHttpServer();

        httpServer.webSocketHandler(webSocket -> {
                    String[] playerIds = new String[1];
                    EntityRef<Object>[] entityRefs = new EntityRef[1];

                    webSocket.handler(buffer -> dispatch(playerIds, entityRefs, webSocket, buffer));

                    webSocket.closeHandler(v -> {
                        if (entityRefs[0] != null) {
                            // 集群分片中的实体不能直接使用PoisonPill
                            // 可以通过发送特定消息让实体自行停止
                            entityRefs[0].tell("stop");
                        }
                        playerMap.remove(playerIds[0]);
                        webSocketMap.remove(playerIds[0]);
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
    private void dispatch(String[] playerIds, EntityRef<Object>[] entityRefs, ServerWebSocket webSocket, Buffer buffer) {
        GameMessage message = GameMessage.parseFrom(buffer.getBytes());
        switch (message.getType()) {
            case CS_LOGIN:
                CSLogin csLogin = message.getCsLogin();
                playerIds[0] = csLogin.getUsername();
                // 获取或创建集群分片实体
                entityRefs[0] = ClusterSharding.get(actorSystem).entityRefFor(
                        PlayerActor.PLAYER_TYPE_KEY, playerIds[0]
                );
                playerMap.put(playerIds[0], entityRefs[0]);
                webSocketMap.put(playerIds[0], webSocket);

                // 发送消息给玩家实体
                entityRefs[0].tell(csLogin);
                break;
            case CS_SEND_MESSAGE:
                if (entityRefs[0] != null) {
                    entityRefs[0].tell(message.getCsSendMessage());
                }
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