package com.felipeb.discordclone.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felipeb.discordclone.chat.api.ChatMessage;
import com.felipeb.discordclone.chat.api.MessageType;
import com.felipeb.discordclone.chat.api.OutgoingMessage;
import com.felipeb.discordclone.chat.broker.MessageBroker;
import com.felipeb.discordclone.chat.session.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * Phase 1 protocol (all payloads are JSON):
 * <pre>
 *   {"type":"CONNECT",         "from":"alice"}
 *   {"type":"DIRECT_MESSAGE",  "to":"bob", "content":"hi"}
 * </pre>
 *
 * Server pushes:
 * <pre>
 *   {"type":"DELIVERED", "from":"alice", "to":"bob", "content":"hi", "timestamp":"..."}
 *   {"type":"ERROR",     "content":"..."}
 * </pre>
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final String USER_ID_ATTR = "userId";

    private final ObjectMapper mapper;
    private final SessionRegistry sessions;
    private final MessageBroker broker;

    public ChatWebSocketHandler(ObjectMapper mapper,
                                SessionRegistry sessions,
                                MessageBroker broker) {
        this.mapper = mapper;
        this.sessions = sessions;
        this.broker = broker;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection opened: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ChatMessage payload = mapper.readValue(message.getPayload(), ChatMessage.class);

        switch (payload.type()) {
            case CONNECT -> handleConnect(session, payload);
            case DIRECT_MESSAGE -> handleDirectMessage(session, payload);
            default -> sendError(session, "Unsupported message type: " + payload.type());
        }
    }

    private void handleConnect(WebSocketSession session, ChatMessage payload) {
        if (payload.from() == null || payload.from().isBlank()) {
            sendError(session, "CONNECT requires 'from' (userId)");
            return;
        }
        session.getAttributes().put(USER_ID_ATTR, payload.from());
        sessions.register(payload.from(), session);
        log.info("User '{}' connected (session {})", payload.from(), session.getId());
    }

    private void handleDirectMessage(WebSocketSession session, ChatMessage payload) {
        Object bound = session.getAttributes().get(USER_ID_ATTR);
        if (!(bound instanceof String sender)) {
            sendError(session, "You must CONNECT before sending messages");
            return;
        }
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

    private void sendError(WebSocketSession session, String error) {
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(new TextMessage(mapper.writeValueAsString(OutgoingMessage.error(error))));
            } catch (IOException e) {
                log.warn("Failed to send error to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.unregister(session);
        log.info("WebSocket connection closed: {} ({})", session.getId(), status);
    }
}
