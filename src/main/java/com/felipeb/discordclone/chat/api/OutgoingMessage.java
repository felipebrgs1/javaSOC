package com.felipeb.discordclone.chat.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutgoingMessage(
        MessageType type,
        String from,
        String to,
        String server,
        String channel,
        String content,
        Long id,
        Instant timestamp
) {

    public static OutgoingMessage authenticated(Long userId, String username) {
        // 'from' carries the username for client convenience
        return new OutgoingMessage(MessageType.AUTHENTICATED, username, null, null, null,
                "userId=" + userId, null, Instant.now());
    }

    public static OutgoingMessage delivered(String from, String to, String content) {
        return new OutgoingMessage(MessageType.DELIVERED, from, to, null, null, content, null, Instant.now());
    }

    public static OutgoingMessage published(Long id, String from, String server, String channel, String content, Instant timestamp) {
        return new OutgoingMessage(MessageType.PUBLISHED, from, null, server, channel, content, id, timestamp);
    }

    public static OutgoingMessage subscribed(String server, String channel) {
        return new OutgoingMessage(MessageType.SUBSCRIBED, null, null, server, channel, null, null, Instant.now());
    }

    public static OutgoingMessage unsubscribed(String server, String channel) {
        return new OutgoingMessage(MessageType.UNSUBSCRIBED, null, null, server, channel, null, null, Instant.now());
    }

    public static OutgoingMessage error(String content) {
        return new OutgoingMessage(MessageType.ERROR, null, null, null, null, content, null, Instant.now());
    }
}
