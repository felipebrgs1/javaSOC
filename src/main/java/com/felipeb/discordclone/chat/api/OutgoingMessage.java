package com.felipeb.discordclone.chat.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutgoingMessage(
        MessageType type,
        String from,
        String to,
        String content,
        Instant timestamp
) {

    public static OutgoingMessage delivered(String from, String to, String content) {
        return new OutgoingMessage(MessageType.DELIVERED, from, to, content, Instant.now());
    }

    public static OutgoingMessage error(String content) {
        return new OutgoingMessage(MessageType.ERROR, null, null, content, Instant.now());
    }
}
