package com.felipeb.discordclone.chat.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
        MessageType type,
        String from,
        String to,
        String server,
        String channel,
        String content,
        String token,
        Long messageId,
        String emoji,
        List<Long> attachmentIds
) {
}
