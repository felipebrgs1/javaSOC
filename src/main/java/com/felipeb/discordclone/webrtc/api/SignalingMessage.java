package com.felipeb.discordclone.webrtc.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.felipeb.discordclone.chat.api.MessageType;

import java.time.Instant;
import java.util.List;

/**
 * Outbound WebRTC signaling message (server -> client).
 * <p>
 * The server only relays SDP/ICE payloads between peers in the same voice
 * room — media itself flows peer-to-peer, never through the server.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignalingMessage(
        MessageType type,
        String server,
        String channel,
        String from,
        String to,
        String content,
        List<String> participants,
        Instant timestamp
) {

    /** Ack to the joining user: room + full participant list (including themselves). */
    public static SignalingMessage joined(String server, String channel, List<String> participants) {
        return new SignalingMessage(MessageType.VOICE_JOINED, server, channel, null, null,
                null, participants, Instant.now());
    }

    /** Broadcast to existing peers: a user just entered the room. */
    public static SignalingMessage userJoined(String server, String channel, String username) {
        return new SignalingMessage(MessageType.VOICE_USER_JOINED, server, channel, username,
                null, null, null, Instant.now());
    }

    /** Broadcast to remaining peers: a user left the room. */
    public static SignalingMessage userLeft(String server, String channel, String username) {
        return new SignalingMessage(MessageType.VOICE_USER_LEFT, server, channel, username,
                null, null, null, Instant.now());
    }

    /** Relayed SDP/ICE payload (SDP_OFFER / SDP_ANSWER / ICE_CANDIDATE). */
    public static SignalingMessage relay(MessageType type, String from, String to,
                                         String server, String channel, String content) {
        return new SignalingMessage(type, server, channel, from, to, content, null, Instant.now());
    }
}
