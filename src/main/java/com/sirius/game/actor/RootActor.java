package com.sirius.game.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class RootActor extends AbstractBehavior<Object> {

    private final transient Map<String, ActorRef<Object>> players;

    public static Behavior<Object> create(Map<String, ActorRef<Object>> players) {
        return Behaviors.setup(context -> new RootActor(context, players));
    }

    private RootActor(ActorContext<Object> context,Map<String, ActorRef<Object>> players) {
        super(context);
        this.players = players;
        log.info("RootActor created");
    }

    @Override
    public Receive<Object> createReceive() {
        return newReceiveBuilder()
                .build();
    }
}