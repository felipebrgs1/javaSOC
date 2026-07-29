package com.felipeb.discordclone.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felipeb.discordclone.chat.api.ChatMessage;
import com.felipeb.discordclone.chat.api.HistoryMessage;
import com.felipeb.discordclone.chat.api.MessageType;
import com.felipeb.discordclone.chat.api.OutgoingMessage;
import com.felipeb.discordclone.chat.broker.MessageBroker;
import com.felipeb.discordclone.chat.channel.Channel;
import com.felipeb.discordclone.chat.channel.ChannelNotFoundException;
import com.felipeb.discordclone.chat.channel.ChannelService;
import com.felipeb.discordclone.chat.channel.Message;
import com.felipeb.discordclone.chat.session.SessionRegistry;
import com.felipeb.discordclone.chat.subscription.ChannelSubscriptions;
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
 * Phase 2 protocol (JSON payloads, all fields are optional unless noted):
 * <pre>
 *   // client -> server
 *   {"type":"CONNECT",          "from":"alice"}
 *   {"type":"DIRECT_MESSAGE",   "to":"bob",          "content":"hi"}
 *   {"type":"SUBSCRIBE",        "channel":"general"}
 *   {"type":"UNSUBSCRIBE",      "channel":"general"}
 *   {"type":"CHANNEL_MESSAGE",  "channel":"general", "content":"hi everyone"}
 *
 *   // server -> client
 *   {"type":"DELIVERED",    "from":"alice", "to":"bob",          "content":"hi",          "timestamp":"..."}
 *   {"type":"PUBLISHED",    "id":1, "from":"alice", "channel":"general", "content":"hi",  "timestamp":"..."}
 *   {"type":"SUBSCRIBED",   "channel":"general", "timestamp":"..."}
 *   {"type":"UNSUBSCRIBED", "channel":"general", "timestamp":"..."}
 *   {"type":"HISTORY",      "channel":"general", "messages":[{"id":1,"from":"alice","content":"hi","timestamp":"..."}]}
 *   {"type":"ERROR",        "content":"..."}
 * </pre>
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final String USER_ID_ATTR = "userId";
    private static final int HISTORY_LIMIT = 50;

    private final ObjectMapper mapper;
    private final SessionRegistry sessions;
    private final ChannelSubscriptions subscriptions;
    private final ChannelService channelService;
    private final MessageBroker broker;

    public ChatWebSocketHandler(ObjectMapper mapper,
                                SessionRegistry sessions,
                                ChannelSubscriptions subscriptions,
                                ChannelService channelService,
                                MessageBroker broker) {
        this.mapper = mapper;
        this.sessions = sessions;
        this.subscriptions = subscriptions;
        this.channelService = channelService;
        this.broker = broker;
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
                case CONNECT -> handleConnect(session, payload);
                case DIRECT_MESSAGE -> handleDirectMessage(session, payload);
                case SUBSCRIBE -> handleSubscribe(session, payload);
                case UNSUBSCRIBE -> handleUnsubscribe(session, payload);
                case CHANNEL_MESSAGE -> handleChannelMessage(session, payload);
                default -> sendError(session, "Unsupported message type: " + payload.type());
            }
        } catch (ChannelNotFoundException e) {
            sendError(session, e.getMessage());
        }
    }

    // ---------- Phase 1: connection + 1:1 DM ----------

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

    // ---------- Phase 2: channels (pub/sub) ----------

    private void handleSubscribe(WebSocketSession session, ChatMessage payload) {
        String user = requireUser(session);
        if (user == null) return;
        if (payload.channel() == null || payload.channel().isBlank()) {
            sendError(session, "SUBSCRIBE requires 'channel'");
            return;
        }
        Channel channel = channelService.requireByName(payload.channel());
        subscriptions.subscribe(channel.getName(), session);

        send(session, OutgoingMessage.subscribed(channel.getName()));

        List<Message> history = channelService.recent(channel, HISTORY_LIMIT);
        List<HistoryMessage.MessageView> views = history.stream()
                .map(m -> new HistoryMessage.MessageView(
                        m.getId(), m.getAuthor(), m.getContent(), m.getCreatedAt()))
                .toList();
        send(session, HistoryMessage.of(channel.getName(), views));
        log.info("User '{}' subscribed to '{}' (history size={})", user, channel.getName(), views.size());
    }

    private void handleUnsubscribe(WebSocketSession session, ChatMessage payload) {
        if (requireUser(session) == null) return;
        if (payload.channel() == null || payload.channel().isBlank()) {
            sendError(session, "UNSUBSCRIBE requires 'channel'");
            return;
        }
        subscriptions.unsubscribe(payload.channel(), session);
        send(session, OutgoingMessage.unsubscribed(payload.channel()));
    }

    private void handleChannelMessage(WebSocketSession session, ChatMessage payload) {
        String sender = requireUser(session);
        if (sender == null) return;
        if (payload.channel() == null || payload.content() == null) {
            sendError(session, "CHANNEL_MESSAGE requires 'channel' and 'content'");
            return;
        }
        Channel channel = channelService.requireByName(payload.channel());
        if (!subscriptions.isSubscribed(channel.getName(), session)) {
            sendError(session, "You must SUBSCRIBE to '" + channel.getName() + "' before posting");
            return;
        }
        Message saved = channelService.publish(channel.getName(), sender, payload.content());
        broker.publishToChannel(channel.getName(),
                OutgoingMessage.published(saved.getId(), sender, channel.getName(),
                        saved.getContent(), saved.getCreatedAt()));
    }

    // ---------- helpers ----------

    private String requireUser(WebSocketSession session) {
        Object bound = session.getAttributes().get(USER_ID_ATTR);
        if (bound instanceof String s) {
            return s;
        }
        sendError(session, "You must CONNECT before sending messages");
        return null;
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
        sessions.unregister(session);
        subscriptions.unsubscribeFromAll(session);
        log.info("WebSocket connection closed: {} ({})", session.getId(), status);
    }
}
