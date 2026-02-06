package com.sirius.game.actor;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.sirius.game.common.ProtobufBinaryUtils;
import com.sirius.game.proto.Message;
import com.sirius.game.proto.SendMessageRequest;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import lombok.extern.slf4j.Slf4j;

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
                .onMessage(Message.class, this::onMessage)
                .build();
    }

    private Behavior<Object> onMessage(Message message) {
        log.info("Player {} received Message: type={}", playerId, message.getType());

        // 根据消息类型处理消息
        switch (message.getType()) {
            case SEND_REQUEST:
                return handleSendMessageRequest(message.getSendRequest());
            default:
                log.warn("Player {} received unknown message type: {}", playerId, message.getType());
                return this;
        }
    }

    private Behavior<Object> handleSendMessageRequest(SendMessageRequest request) {
        log.info("Player {} handling send message request: from={}, to={}, content={}",
                playerId, request.getFrom(), request.getTo(), request.getContent());

        // 创建接收消息通知
        Message notification = ProtobufBinaryUtils.createReceiveMessageNotification(
                request.getFrom(), request.getTo(), request.getContent(), request.getFrom());

        // 向WebSocket发送通知（二进制格式）
        try {
            // 如果消息是发给自己的，直接发送通知
            if (playerId.equals(request.getTo())) {
                byte[] notificationBytes = ProtobufBinaryUtils.serializeMessage(notification);
                webSocket.writeBinaryMessage(Buffer.buffer(notificationBytes));
                log.info("Player {} sent notification to self", playerId);
            } else {
                log.info("Player {} received message for different recipient: {}", playerId, request.getTo());
            }
        } catch (Exception e) {
            log.error("Failed to send notification to WebSocket", e);
        }

        return this;
    }
}