package com.sirius.game.actor;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.google.protobuf.InvalidProtocolBufferException;
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
                .onMessage(byte[].class, this::onMessage)
                .build();
    }

    private Behavior<Object> onMessage(byte[] data) throws InvalidProtocolBufferException {
        GameMessage message = GameMessage.parseFrom(data);
        log.info("Player {} received Message: type={}", playerId, message.getType());

        // 根据消息类型处理消息
        switch (message.getType()) {
            case CS_LOGIN:
                return handleLoginRequest(message.getCsLogin());
            case CS_SEND_MESSAGE:
                return handleSendMessageRequest(message.getCsSendMessage());
            default:
                log.warn("Player {} received unknown message type: {}", playerId, message.getType());
                return this;
        }
    }

    private Behavior<Object> handleLoginRequest(CSLogin csLogin) {
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

    private Behavior<Object> handleSendMessageRequest(CSSendMessage csSendMessage) {
        SCReceiveMessage scReceiveMessage = SCReceiveMessage.newBuilder()
                .setFrom(csSendMessage.getFrom())
                .setTo(csSendMessage.getTo())
                .setContent(csSendMessage.getContent())
                .setTimestamp(System.currentTimeMillis())
                .setMessageId(UUID.randomUUID().toString())
                .build();

        GameMessage gameMessage = GameMessage.newBuilder()
                .setType(MessageType.SC_RECEIVE_MESSAGE)
                .setScReceiveMessage(scReceiveMessage)
                .build();

        webSocket.writeBinaryMessage(Buffer.buffer(gameMessage.toByteArray()));
        return this;
    }
}