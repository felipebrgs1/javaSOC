package com.felipeb.discordclone.webrtc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felipeb.discordclone.chat.ChatWebSocketHandler;
import com.felipeb.discordclone.chat.api.ChatMessage;
import com.felipeb.discordclone.chat.api.MessageType;
import com.felipeb.discordclone.chat.api.OutgoingMessage;
import com.felipeb.discordclone.chat.channel.Channel;
import com.felipeb.discordclone.chat.channel.ChannelNotFoundException;
import com.felipeb.discordclone.chat.channel.ChannelService;
import com.felipeb.discordclone.chat.channel.ChannelType;
import com.felipeb.discordclone.chat.permission.PermissionDeniedException;
import com.felipeb.discordclone.chat.permission.PermissionService;
import com.felipeb.discordclone.chat.session.SessionRegistry;
import com.felipeb.discordclone.server.Server;
import com.felipeb.discordclone.server.ServerRepository;
import com.felipeb.discordclone.webrtc.api.SignalingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Handles the signaling side of WebRTC voice.
 * <p>
 * The server never sees audio: it only routes SDP offers/answers and ICE
 * candidates between peers inside the same voice room. Media flows P2P.
 */
@Component
public class VoiceSignalingHandler {

    private static final Logger log = LoggerFactory.getLogger(VoiceSignalingHandler.class);

    private final ObjectMapper mapper;
    private final VoiceRoomRegistry rooms;
    private final SessionRegistry sessions;
    private final ServerRepository servers;
    private final ChannelService channelService;
    private final PermissionService permissions;

    public VoiceSignalingHandler(ObjectMapper mapper,
                                 VoiceRoomRegistry rooms,
                                 SessionRegistry sessions,
                                 ServerRepository servers,
                                 ChannelService channelService,
                                 PermissionService permissions) {
        this.mapper = mapper;
        this.rooms = rooms;
        this.sessions = sessions;
        this.servers = servers;
        this.channelService = channelService;
        this.permissions = permissions;
    }

    /** Routes voice signaling messages; returns false if the type is not voice-related. */
    public boolean handleMessage(WebSocketSession session, ChatMessage payload) {
        switch (payload.type()) {
            case VOICE_JOIN -> handleJoin(session, payload);
            case VOICE_LEAVE -> handleLeave(session, payload);
            case SDP_OFFER, SDP_ANSWER, ICE_CANDIDATE -> handleRelay(session, payload);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Disconnect cleanup: leaves every voice room and notifies remaining peers. */
    public void onSessionClosed(WebSocketSession session) {
        Object nameObj = session.getAttributes().get(ChatWebSocketHandler.USERNAME_ATTR);
        if (!(nameObj instanceof String username)) {
            return;
        }
        for (String room : rooms.leaveAll(username)) {
            RoomKey key = RoomKey.parse(room);
            if (key == null) continue;
            for (String peer : rooms.participants(room)) {
                sendToUser(peer, SignalingMessage.userLeft(key.server(), key.channel(), username));
            }
        }
    }

    // ---------- handlers ----------

    private void handleJoin(WebSocketSession session, ChatMessage payload) {
        AuthedUser user = requireAuthed(session);
        if (user == null) return;
        Server server = requireServer(session, payload.server());
        if (server == null) return;
        Channel channel = resolveChannel(session, server, payload.channel());
        if (channel == null) return;

        permissions.requireReadAccess(user.userId(), server.getId());
        if (channel.getType() != ChannelType.VOICE) {
            throw new IllegalArgumentException("#" + channel.getName() + " is not a voice channel");
        }

        String room = roomKey(server, channel);
        Set<String> preExisting = rooms.join(room, user.username());

        List<String> participants = new ArrayList<>();
        participants.add(user.username());
        participants.addAll(preExisting);
        send(session, SignalingMessage.joined(server.getName(), channel.getName(), participants));

        for (String peer : preExisting) {
            sendToUser(peer, SignalingMessage.userJoined(server.getName(), channel.getName(), user.username()));
        }
        log.info("User '{}' joined voice room '{}' (participants={})", user.username(), room, preExisting.size());
    }

    private void handleLeave(WebSocketSession session, ChatMessage payload) {
        AuthedUser user = requireAuthed(session);
        if (user == null) return;
        Server server = requireServer(session, payload.server());
        if (server == null) return;
        Channel channel = resolveChannel(session, server, payload.channel());
        if (channel == null) return;

        String room = roomKey(server, channel);
        Set<String> remaining = rooms.leave(room, user.username());
        for (String peer : remaining) {
            sendToUser(peer, SignalingMessage.userLeft(server.getName(), channel.getName(), user.username()));
        }
        log.info("User '{}' left voice room '{}'", user.username(), room);
    }

    private void handleRelay(WebSocketSession session, ChatMessage payload) {
        AuthedUser user = requireAuthed(session);
        if (user == null) return;
        Server server = requireServer(session, payload.server());
        if (server == null) return;
        Channel channel = resolveChannel(session, server, payload.channel());
        if (channel == null) return;

        String target = payload.to();
        if (target == null || target.isBlank()) {
            sendError(session, payload.type() + " requires 'to'");
            return;
        }
        if (target.equals(user.username())) {
            sendError(session, "You cannot signal yourself");
            return;
        }
        if (payload.content() == null || payload.content().isBlank()) {
            sendError(session, payload.type() + " requires 'content'");
            return;
        }

        String room = roomKey(server, channel);
        if (!rooms.isInRoom(room, user.username()) || !rooms.isInRoom(room, target)) {
            sendError(session, "Both peers must be in voice room '" + room + "'");
            return;
        }

        sendToUser(target, SignalingMessage.relay(payload.type(), user.username(), target,
                server.getName(), channel.getName(), payload.content()));
    }

    // ---------- helpers ----------

    private record AuthedUser(Long userId, String username) {}

    private AuthedUser requireAuthed(WebSocketSession session) {
        Object id = session.getAttributes().get(ChatWebSocketHandler.USER_ID_ATTR);
        Object name = session.getAttributes().get(ChatWebSocketHandler.USERNAME_ATTR);
        if (id instanceof Long userId && name instanceof String username) {
            return new AuthedUser(userId, username);
        }
        sendError(session, "You must AUTH before sending messages");
        return null;
    }

    private Server requireServer(WebSocketSession session, String name) {
        if (name == null || name.isBlank()) {
            sendError(session, "Missing 'server'");
            return null;
        }
        return servers.findByName(name).orElseThrow(() ->
                new IllegalArgumentException("Server not found: " + name));
    }

    private Channel resolveChannel(WebSocketSession session, Server server, String name) {
        if (name == null || name.isBlank()) {
            sendError(session, "Missing 'channel'");
            return null;
        }
        return channelService.requireByServerAndName(server, name);
    }

    private static String roomKey(Server server, Channel channel) {
        return server.getName() + ":" + channel.getName();
    }

    private record RoomKey(String server, String channel) {
        static RoomKey parse(String key) {
            int colon = key.indexOf(':');
            if (colon <= 0 || colon == key.length() - 1) {
                return null;
            }
            return new RoomKey(key.substring(0, colon), key.substring(colon + 1));
        }
    }

    private void sendToUser(String username, Object payload) {
        Optional<WebSocketSession> target = sessions.findByUserId(username);
        if (target.isEmpty()) return;
        WebSocketSession session = target.get();
        synchronized (session) {
            if (session.isOpen()) {
                send(session, payload);
            }
        }
    }

    private void send(WebSocketSession session, Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payload for session {}", session.getId(), e);
        } catch (IOException e) {
            log.warn("Failed to send to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String error) {
        send(session, OutgoingMessage.error(error));
    }
}
