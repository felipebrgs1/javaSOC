package com.felipeb.discordclone.chat.broker;

import com.felipeb.discordclone.chat.api.OutgoingMessage;

/**
 * Routes outgoing messages to the right destination.
 * <p>
 * Implementation-agnostic: Phase 1 uses an in-memory broker, but the
 * interface is designed so a RabbitMQ/Kafka-backed implementation
 * can be dropped in later without touching the handler.
 */
public interface MessageBroker {

    void sendToUser(String userId, OutgoingMessage message);

    void broadcast(OutgoingMessage message);
}
