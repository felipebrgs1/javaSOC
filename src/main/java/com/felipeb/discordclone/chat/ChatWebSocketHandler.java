package com.felipeb.discordclone.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felipeb.discordclone.auth.JwtService;
import com.felipeb.discordclone.auth.UserService;
import com.felipeb.discordclone.chat.api.ChatMessage;
import com.felipeb.discordclone.chat.api.HistoryMessage;
import com.felipeb.discordclone.chat.api.MessageType;
import com.felipeb.discordclone.chat.api.OutgoingMessage;
import com.felipeb.discordclone.chat.broker.MessageBroker;
import com.felipeb.discordclone.chat.channel.Channel;
import com.felipeb.discordclone.chat.channel.ChannelNotFoundException;
import com.felipeb.discordclone.chat.channel.ChannelService;
import com.felipeb.discordclone.chat.channel.Message;
import com.felipeb.discordclone.chat.permission.PermissionDeniedException;
import com.felipeb.discordclone.chat.permission.PermissionService;
import com.felipeb.discordclone.chat.presence.PresenceService;
import com.felipeb.discordclone.chat.session.SessionRegistry;
import com.felipeb.discordclone.chat.subscription.ChannelSubscriptions;
import com.felipeb.discordclone.server.Server;
import com.felipeb.discordclone.server.ServerRepository;
import com.felipeb.discordclone.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;

/**
 * Phase 4 protocol — see Javadoc on each handler method. Notable additions:
 * <pre>
 *   // client -> server
 *   {"type":"HEARTBEAT"}                                   // no payload; resets idle timer
 *
 *   // server -> client (broadcast on a channel)
 *   {"type":"PRESENCE_UPDATED", "userId":1, "username":"alice", "status":"ONLINE"}
 * </pre>
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final String USER_ID_ATTR = "userId";
    private static final String USERNAME_ATTR = "username";
    private static final int HISTORY_LIMIT = 50;

    private final ObjectMapper mapper;
    private final SessionRegistry sessions;
    private final ChannelSubscriptions subscriptions;
    private final ChannelService channelService;
    private final ServerRepository servers;
    private final UserService userService;
    private final JwtService jwt;
    private final PermissionService permissions;
    private final MessageBroker broker;
    private final PresenceService presence;

    public ChatWebSocketHandler(ObjectMapper mapper,
                                SessionRegistry sessions,
                                ChannelSubscriptions subscriptions,
                                ChannelService channelService,
                                ServerRepository servers,
                                UserService userService,
                                JwtService jwt,
                                PermissionService permissions,
                                MessageBroker broker,
                                PresenceService presence) {
        this.mapper = mapper;
        this.sessions = sessions;
        this.subscriptions = subscriptions;
        this.channelService = channelService;
        this.servers = servers;
        this.userService = userService;
        this.jwt = jwt;
        this.permissions = permissions;
        this.broker = broker;
        this.presence = presence;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection opened: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ChatMessage payload = mapper.readValue(message.getPayload(), ChatMessage.class);

        try {
            switch (payload.type()) {
                case AUTH -> handleAuth(session, payload);
                case HEARTBEAT -> handleHeartbeat(session);
                case DIRECT_MESSAGE -> handleDirectMessage(session, payload);
                case SUBSCRIBE -> handleSubscribe(session, payload);
                case UNSUBSCRIBE -> handleUnsubscribe(session, payload);
                case CHANNEL_MESSAGE -> handleChannelMessage(session, payload);
                default -> sendError(session, "Unsupported message type: " + payload.type());
            }
        } catch (PermissionDeniedException | ChannelNotFoundException | IllegalArgumentException e) {
            sendError(session, e.getMessage());
        }
    }

    // ---------- AUTH ----------

    private void handleAuth(WebSocketSession session, ChatMessage payload) {
        if (payload.token() == null || payload.token().isBlank()) {
            sendError(session, "AUTH requires 'token'");
            closeAsUnauthorized(session);
            return;
        }
        try {
            JwtService.AuthenticatedUser auth = jwt.parse(payload.token());
            User user = userService.findById(auth.userId()).orElseThrow(() ->
                    new IllegalStateException("Token subject does not match a user"));

            session.getAttributes().put(USER_ID_ATTR, user.getId());
            session.getAttributes().put(USERNAME_ATTR, user.getUsername());
            sessions.register(user.getUsername(), session);

            send(session, OutgoingMessage.authenticated(user.getId(), user.getUsername()));
            presence.onAuthenticated(user.getId(), user.getUsername());
            log.info("User '{}' authenticated (session {})", user.getUsername(), session.getId());
        } catch (Exception e) {
            log.info("AUTH rejected for session {}: {}", session.getId(), e.getMessage());
            sendError(session, "Invalid token");
            closeAsUnauthorized(session);
        }
    }

    private void closeAsUnauthorized(WebSocketSession session) {
        try {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        } catch (IOException ignored) {
            // best-effort
        }
    }

    // ---------- HEARTBEAT (Phase 4) ----------

    private void handleHeartbeat(WebSocketSession session) {
        AuthedUser user = requireAuthed(session);
        if (user == null) return;
        presence.onHeartbeat(user.userId(), user.username());
    }

    // ---------- 1:1 DM ----------

    private void handleDirectMessage(WebSocketSession session, ChatMessage payload) {
        String sender = requireUser(session);
        if (sender == null) return;
        if (payload.to() == null || payload.content() == null) {
            sendError(session, "DIRECT_MESSAGE requires 'to' and 'content'");
            return;
        }
        if (!sessions.isOnline(payload.to())) {
            sendError(session, "Recipient '" + payload.to() + "' is not online");
            return;
        }
        broker.sendToUser(payload.to(), OutgoingMessage.delivered(sender, payload.to(), payload.content()));
    }

    // ---------- Channels ----------

    private void handleSubscribe(WebSocketSession session, ChatMessage payload) {
        AuthedUser user = requireAuthed(session);
        if (user == null) return;
        Server server = requireServer(session, payload.server());
        if (server == null) return;
        Channel channel = resolveChannel(session, server, payload.channel());
        if (channel == null) return;

        permissions.requireReadAccess(user.userId(), server.getId());
        String channelKey = channelKey(server, channel);

        // Snapshot pre-existing subscribers BEFORE adding self, so the
        // presence service doesn't see self in the list (and so concurrent
        // subscribes don't double-announce each other).
        java.util.Collection<WebSocketSession> preExisting = subscriptions.subscribersOf(channelKey);
        subscriptions.subscribe(channelKey, session);

        send(session, OutgoingMessage.subscribed(server.getName(), channel.getName()));

        List<HistoryMessage.MessageView> views = channelService.recentViews(channel, HISTORY_LIMIT);
        send(session, HistoryMessage.of(server.getName(), channel.getName(), views));

        // Phase 4: announce presence to pre-existing subscribers, and snapshot
        // their current status to the new subscriber.
        presence.onSubscribed(session, user.userId(), user.username(), channelKey, preExisting);

        log.info("User '{}' subscribed to '{}:{}' (history size={}, preExisting={})",
                user.username(), server.getName(), channel.getName(), views.size(), preExisting.size());
    }

    private void handleUnsubscribe(WebSocketSession session, ChatMessage payload) {
        AuthedUser user = requireAuthed(session);
        if (user == null) return;
        Server server = requireServer(session, payload.server());
        if (server == null) return;
        Channel channel = resolveChannel(session, server, payload.channel());
        if (channel == null) return;
        subscriptions.unsubscribe(channelKey(server, channel), session);
        send(session, OutgoingMessage.unsubscribed(server.getName(), channel.getName()));
    }

    private void handleChannelMessage(WebSocketSession session, ChatMessage payload) {
        AuthedUser user = requireAuthed(session);
        if (user == null) return;
        Server server = requireServer(session, payload.server());
        if (server == null) return;
        Channel channel = resolveChannel(session, server, payload.channel());
        if (channel == null) return;
        if (payload.content() == null) {
            sendError(session, "CHANNEL_MESSAGE requires 'content'");
            return;
        }

        permissions.requireWriteAccess(user.userId(), server.getId());
        String channelKey = channelKey(server, channel);
        if (!subscriptions.isSubscribed(channelKey, session)) {
            sendError(session, "You must SUBSCRIBE to '" + server.getName() + ":" + channel.getName() + "' before posting");
            return;
        }
        User author = userService.findById(user.userId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists"));
        Message saved = channelService.publish(channel, author, payload.content());
        broker.publishToChannel(channelKey,
                OutgoingMessage.published(saved.getId(), author.getUsername(),
                        server.getName(), channel.getName(), saved.getContent(), saved.getCreatedAt()));
    }

    // ---------- helpers ----------

    private record AuthedUser(Long userId, String username) {}

    private AuthedUser requireAuthed(WebSocketSession session) {
        Object id = session.getAttributes().get(USER_ID_ATTR);
        Object name = session.getAttributes().get(USERNAME_ATTR);
        if (id instanceof Long userId && name instanceof String username) {
            return new AuthedUser(userId, username);
        }
        sendError(session, "You must AUTH before sending messages");
        return null;
    }

    private String requireUser(WebSocketSession session) {
        AuthedUser u = requireAuthed(session);
        return u == null ? null : u.username();
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

    private static String channelKey(Server server, Channel channel) {
        return server.getName() + ":" + channel.getName();
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

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object idObj = session.getAttributes().get(USER_ID_ATTR);
        Object nameObj = session.getAttributes().get(USERNAME_ATTR);
        if (idObj instanceof Long userId && nameObj instanceof String username) {
            presence.onDisconnected(session, userId, username);
        }
        sessions.unregister(session);
        subscriptions.unsubscribeFromAll(session);
        log.info("WebSocket connection closed: {} ({})", session.getId(), status);
    }
}
