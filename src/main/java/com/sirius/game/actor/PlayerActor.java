package com.sirius.game.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.sirius.game.proto.CreatePlayer;
import com.sirius.game.proto.PlayerMessage;
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
                .onMessage(PlayerMessage.class, this::onPlayerMessage)
                .build();
    }

    private Behavior<Object> onPlayerMessage(PlayerMessage message) {
        log.info("Player {} received message: {}", playerId, message.content());
        webSocket.writeTextMessage(message.content());
        return this;
    }

    private Behavior<Object> onCreatePlayer(CreatePlayer command) {
        // 注意：由于Actor模型的限制，我们不能直接将ServerWebSocket传递给Actor
        // 这里我们只能创建PlayerActor，WebSocket需要在BootConfig中处理
        //ActorRef<Object> playerActor = getContext().spawn(PlayerActor.create(command.playerId()), "player-" + command.playerId());
        return this;
    }
}