package com.felipeb.discordclone.webrtc;

import java.util.Set;

/**
 * Tracks which users are currently inside which voice room.
 * <p>
 * A user can only be in one voice room at a time. Pure in-memory;
 * becomes Redis-backed when we add multiple server instances (Phase 3+).
 */
public interface VoiceRoomRegistry {

    /**
     * Puts the user into the room. If the user was in another room, they are
     * moved (leaving the previous one). Returns the usernames already present,
     * excluding the joining user.
     */
    Set<String> join(String room, String username);

    /** Removes the user from the room. Returns the usernames still present. */
    Set<String> leave(String room, String username);

    /** Removes the user from every room (disconnect cleanup).
     *  Returns the rooms they were removed from. */
    Set<String> leaveAll(String username);

    /** Usernames currently in the room (may include the caller). */
    Set<String> participants(String room);

    /** True if the user is currently inside the room. */
    boolean isInRoom(String room, String username);
}
