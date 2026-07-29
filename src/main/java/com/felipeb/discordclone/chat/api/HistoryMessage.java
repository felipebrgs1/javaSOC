package com.felipeb.discordclone.chat.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryMessage(
        MessageType type,
        String server,
        String channel,
        List<MessageView> messages
) {

    public record MessageView(
            Long id,
            String from,
            String content,
            Instant timestamp
    ) {
    }

    public static HistoryMessage of(String server, String channel, List<MessageView> messages) {
        return new HistoryMessage(MessageType.HISTORY, server, channel, messages);
    }
}
