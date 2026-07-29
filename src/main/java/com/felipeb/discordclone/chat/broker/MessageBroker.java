package com.felipeb.discordclone.chat.broker;

import com.felipeb.discordclone.chat.api.OutgoingMessage;

/**
 * Routes outgoing messages to the right destination.
 * <p>
 * Implementation-agnostic: Phase 1 in-memory, Phase 2 keeps it in-memory
 * but adds pub/sub on channels. A RabbitMQ/Kafka-backed implementation
 * can be dropped in later without touching the handler.
 */
public interface MessageBroker {

    void sendToUser(String userId, OutgoingMessage message);

    void broadcast(OutgoingMessage message);

    void publishToChannel(String channelId, OutgoingMessage message);
}
