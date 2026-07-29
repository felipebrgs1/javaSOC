package com.felipeb.discordclone.chat.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutgoingMessage(
        MessageType type,
        String from,
        String to,
        String server,
        String channel,
        String content,
        Long id,
        Long messageId,
        String emoji,
        Long reactorId,
        String reactorUsername,
        List<ReactionView> reactions,
        List<AttachmentView> attachments,
        Instant editedAt,
        Instant timestamp
) {

    public record ReactionView(String emoji, long count, boolean includesMe) {}
    public record AttachmentView(Long id, String filename, String contentType, long sizeBytes, String url) {}

    public static OutgoingMessage authenticated(Long userId, String username) {
        return new OutgoingMessage(MessageType.AUTHENTICATED, username, null, null, null,
                "userId=" + userId, null, null, null, null, null, null, null, null, Instant.now());
    }

    public static OutgoingMessage delivered(String from, String to, String content) {
        return new OutgoingMessage(MessageType.DELIVERED, from, to, null, null, content, null, null, null, null, null, null, null, null, Instant.now());
    }

    public static OutgoingMessage published(Long id, String from, String server, String channel, String content, Instant timestamp) {
        return new OutgoingMessage(MessageType.PUBLISHED, from, null, server, channel, content, id, null, null, null, null, null, null, null, timestamp);
    }

    public static OutgoingMessage subscribed(String server, String channel) {
        return new OutgoingMessage(MessageType.SUBSCRIBED, null, null, server, channel, null, null, null, null, null, null, null, null, null, Instant.now());
    }

    public static OutgoingMessage unsubscribed(String server, String channel) {
        return new OutgoingMessage(MessageType.UNSUBSCRIBED, null, null, server, channel, null, null, null, null, null, null, null, null, null, Instant.now());
    }

    public static OutgoingMessage error(String content) {
        return new OutgoingMessage(MessageType.ERROR, null, null, null, null, content, null, null, null, null, null, null, null, null, Instant.now());
    }

    public static OutgoingMessage messageEdited(Long id, String from, String server, String channel, String content, Instant editedAt) {
        return new OutgoingMessage(MessageType.MESSAGE_EDITED, from, null, server, channel, content, null, id, null, null, null, null, null, editedAt, Instant.now());
    }

    public static OutgoingMessage messageDeleted(Long id, String server, String channel) {
        return new OutgoingMessage(MessageType.MESSAGE_DELETED, null, null, server, channel, null, null, id, null, null, null, null, null, null, Instant.now());
    }

    public static OutgoingMessage reactionAdded(Long messageId, String server, String channel, Long reactorId, String reactorUsername, String emoji) {
        return new OutgoingMessage(MessageType.REACTION_ADDED, null, null, server, channel, null, null, messageId, emoji, reactorId, reactorUsername, null, null, null, Instant.now());
    }

    public static OutgoingMessage reactionRemoved(Long messageId, String server, String channel, Long reactorId, String reactorUsername, String emoji) {
        return new OutgoingMessage(MessageType.REACTION_REMOVED, null, null, server, channel, null, null, messageId, emoji, reactorId, reactorUsername, null, null, null, Instant.now());
    }
}
