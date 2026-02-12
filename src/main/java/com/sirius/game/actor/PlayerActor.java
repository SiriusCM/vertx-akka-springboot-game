package com.sirius.game.actor;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import com.sirius.game.config.WebSocketConfig;
import com.sirius.game.proto.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * 支持集群分片的玩家Actor
 */
@Slf4j
public class PlayerActor extends AbstractBehavior<Object> {
    // 定义分片实体类型键
    public static final EntityTypeKey<Object> PLAYER_TYPE_KEY = EntityTypeKey.create(Object.class, "Player");

    private final ClusterSharding sharding;

    private final String playerId;

    private transient ServerWebSocket webSocket;

    public static Behavior<Object> create(String playerId) {
        return Behaviors.setup(context -> new PlayerActor(context, playerId));
    }

    private PlayerActor(ActorContext<Object> context, String playerId) {
        super(context);
        this.playerId = playerId;
        this.sharding = ClusterSharding.get(context.getSystem());
        log.info("PlayerActorSharding created for player: {}", playerId);
    }

    @Override
    public Receive<Object> createReceive() {
        return newReceiveBuilder()
                .onMessage(CSLogin.class, this::onMessage)
                .onMessage(CSSendMessage.class, this::onMessage)
                .onMessage(SCReceiveMessage.class, this::onMessage)
                .build();
    }

    private Behavior<Object> onMessage(CSLogin csLogin) {
        this.webSocket = WebSocketConfig.webSocketMap.get(playerId);

        SCLoginResult scLoginResult = SCLoginResult.newBuilder()
                .setSuccess(true)
                .setMessage("")
                .setUserId(playerId)
                .setSessionToken("")
                .setTimestamp(System.currentTimeMillis())
                .build();

        GameMessage gameMessage = GameMessage.newBuilder()
                .setType(MessageType.SC_LOGIN_RESULT)
                .setScLoginResult(scLoginResult)
                .build();

        webSocket.writeBinaryMessage(Buffer.buffer(gameMessage.toByteArray()));
        return this;
    }

    private Behavior<Object> onMessage(CSSendMessage csSendMessage) {
        String toPlayerId = csSendMessage.getTo();

        // 创建接收消息
        SCReceiveMessage scReceiveMessage = SCReceiveMessage.newBuilder()
                .setFrom(csSendMessage.getFrom())
                .setTo(csSendMessage.getTo())
                .setContent(csSendMessage.getContent())
                .setTimestamp(System.currentTimeMillis())
                .setMessageId(UUID.randomUUID().toString())
                .build();

        // 通过集群分片发送消息给目标玩家
        EntityRef<Object> targetPlayer = sharding.entityRefFor(PLAYER_TYPE_KEY, toPlayerId);
        targetPlayer.tell(scReceiveMessage);

        log.info("Player {} sent message to {} via cluster sharding: {}", playerId, toPlayerId, csSendMessage.getContent());
        return this;
    }

    private Behavior<Object> onMessage(SCReceiveMessage scReceiveMessage) {
        // 收到消息，通过WebSocket发送给客户端
        GameMessage gameMessage = GameMessage.newBuilder()
                .setType(MessageType.SC_RECEIVE_MESSAGE)
                .setScReceiveMessage(scReceiveMessage)
                .build();

        webSocket.writeBinaryMessage(Buffer.buffer(gameMessage.toByteArray()));
        log.info("Player {} received message from {}: {}", playerId, scReceiveMessage.getFrom(), scReceiveMessage.getContent());
        return this;
    }

    /**
     * 初始化集群分片
     */
    public static void initSharding(ActorSystem<Object> actorSystem) {
        ClusterSharding.get(actorSystem).init(
                Entity.of(PLAYER_TYPE_KEY, entityContext ->
                        PlayerActor.create(entityContext.getEntityId())).withRole("game-node")
        );
        log.info("PlayerActorSharding initialized with cluster sharding");
    }
}