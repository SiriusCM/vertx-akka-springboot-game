package com.sirius.game.actor;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GlobalActor extends AbstractBehavior<Object> {

    public static Behavior<Object> create() {
        return Behaviors.setup(GlobalActor::new);
    }

    private GlobalActor(ActorContext<Object> context) {
        super(context);
        log.info("GlobalActor created");
    }

    @Override
    public Receive<Object> createReceive() {
        return newReceiveBuilder()
                .build();
    }
}