package com.felipeb.discordclone.chat.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felipeb.discordclone.chat.session.SessionRegistry;
import com.felipeb.discordclone.chat.subscription.ChannelSubscriptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
public class InMemoryMessageBroker implements MessageBroker {

    private static final Logger log = LoggerFactory.getLogger(InMemoryMessageBroker.class);

    private final SessionRegistry sessions;
    private final ChannelSubscriptions subscriptions;
    private final ObjectMapper mapper;

    public InMemoryMessageBroker(SessionRegistry sessions,
                                 ChannelSubscriptions subscriptions,
                                 ObjectMapper mapper) {
        this.sessions = sessions;
        this.subscriptions = subscriptions;
        this.mapper = mapper;
    }

    @Override
    public void sendToUser(String userId, Object message) {
        sessions.findByUserId(userId).ifPresent(session -> write(session, message));
    }

    @Override
    public void broadcast(Object message) {
        sessions.all().forEach(session -> write(session, message));
    }

    @Override
    public void publishToChannel(String channelId, Object message) {
        subscriptions.subscribersOf(channelId).forEach(session -> write(session, message));
    }

    @Override
    public void sendToSession(WebSocketSession session, Object message) {
        write(session, message);
    }

    private void write(WebSocketSession session, Object message) {
        // WebSocketSession is not thread-safe for concurrent sendMessage() calls;
        // synchronizing on the session prevents interleaved frame writes.
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize message for session {}", session.getId(), e);
            } catch (IOException | IllegalStateException e) {
                // IllegalStateException: session closed between isOpen() and sendMessage()
                // (common when broadcasting to a session that just disconnected).
                log.debug("Skipping send to closed session {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}
