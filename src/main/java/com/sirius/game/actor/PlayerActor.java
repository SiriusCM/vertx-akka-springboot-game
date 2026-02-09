package com.sirius.game.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.Adapter;
import akka.cluster.Cluster;
import com.sirius.game.proto.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class PlayerActor extends AbstractBehavior<Object> {

    private final String playerId;
    private final transient ServerWebSocket webSocket;

    public static Behavior<Object> create(String playerId, ServerWebSocket webSocket) {
        return Behaviors.setup(context -> new PlayerActor(context, playerId, webSocket));
    }

    private PlayerActor(ActorContext<Object> context, String playerId, ServerWebSocket webSocket) {
        super(context);
        this.playerId = playerId;
        this.webSocket = webSocket;
        log.info("PlayerActor created for player: {}", playerId);
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
        SCLoginResult scLoginResult = SCLoginResult.newBuilder()
                .setSuccess(true)
                .setMessage("")
                .setUserId("")
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

        // 使用适配器获取经典ActorSystem，以便使用ActorSelection
        akka.actor.ActorSystem classicSystem = Adapter.toClassic(getContext().getSystem());
        
        // 尝试通过ActorSelection查找目标玩家（跨节点）
        akka.actor.ActorSelection targetPlayerSelection = classicSystem.actorSelection(
            "player-" + toPlayerId
        );
        
        // 发送消息给目标玩家
        targetPlayerSelection.tell(scReceiveMessage, akka.actor.ActorRef.noSender());
        
        log.info("Player {} sent message to {}: {}", playerId, toPlayerId, csSendMessage.getContent());
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
}