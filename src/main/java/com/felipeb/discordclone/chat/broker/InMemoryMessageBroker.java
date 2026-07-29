package com.felipeb.discordclone.chat.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felipeb.discordclone.chat.api.OutgoingMessage;
import com.felipeb.discordclone.chat.session.SessionRegistry;
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
    private final ObjectMapper mapper;

    public InMemoryMessageBroker(SessionRegistry sessions, ObjectMapper mapper) {
        this.sessions = sessions;
        this.mapper = mapper;
    }

    @Override
    public void sendToUser(String userId, OutgoingMessage message) {
        sessions.findByUserId(userId).ifPresent(session -> write(session, message));
    }

    @Override
    public void broadcast(OutgoingMessage message) {
        sessions.all().forEach(session -> write(session, message));
    }

    private void write(WebSocketSession session, OutgoingMessage message) {
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
            } catch (IOException e) {
                log.warn("Failed to send message to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}
