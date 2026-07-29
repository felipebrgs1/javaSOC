package com.felipeb.discordclone.chat.api;

import com.felipeb.discordclone.chat.presence.PresenceStatus;

public record PresenceUpdateMessage(
        MessageType type,
        Long userId,
        String username,
        PresenceStatus status
) {
    public static PresenceUpdateMessage of(Long userId, String username, PresenceStatus status) {
        return new PresenceUpdateMessage(MessageType.PRESENCE_UPDATED, userId, username, status);
    }
}
