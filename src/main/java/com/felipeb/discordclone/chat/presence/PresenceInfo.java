package com.felipeb.discordclone.chat.presence;

import java.time.Instant;

/**
 * Snapshot of a user's presence. {@code lastSeen} is updated on every
 * heartbeat / connect; status is derived by the scheduler based on
 * elapsed time since lastSeen.
 */
public record PresenceInfo(Long userId, String username, PresenceStatus status, Instant lastSeen) {
}
