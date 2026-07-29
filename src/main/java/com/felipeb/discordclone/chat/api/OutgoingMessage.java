package com.felipeb.discordclone.chat.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutgoingMessage(
        MessageType type,
        String from,
        String to,
        String channel,
        String content,
        Long id,
        Instant timestamp
) {

    public static OutgoingMessage delivered(String from, String to, String content) {
        return new OutgoingMessage(MessageType.DELIVERED, from, to, null, content, null, Instant.now());
    }

    public static OutgoingMessage published(Long id, String from, String channel, String content, Instant timestamp) {
        return new OutgoingMessage(MessageType.PUBLISHED, from, null, channel, content, id, timestamp);
    }

    public static OutgoingMessage subscribed(String channel) {
        return new OutgoingMessage(MessageType.SUBSCRIBED, null, null, channel, null, null, Instant.now());
    }

    public static OutgoingMessage unsubscribed(String channel) {
        return new OutgoingMessage(MessageType.UNSUBSCRIBED, null, null, channel, null, null, Instant.now());
    }

    public static OutgoingMessage error(String content) {
        return new OutgoingMessage(MessageType.ERROR, null, null, null, content, null, Instant.now());
    }
}
