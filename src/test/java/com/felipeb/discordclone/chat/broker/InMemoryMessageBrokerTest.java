package com.felipeb.discordclone.chat.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felipeb.discordclone.chat.api.OutgoingMessage;
import com.felipeb.discordclone.chat.session.SessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InMemoryMessageBrokerTest {

    private SessionRegistry sessions;
    private InMemoryMessageBroker broker;

    @BeforeEach
    void setUp() {
        sessions = mock(SessionRegistry.class);
        // findAndRegisterModules() mimics Spring Boot's auto-configured ObjectMapper
        // so the JavaTimeModule (jackson-datatype-jsr310) is picked up.
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        broker = new InMemoryMessageBroker(sessions, mapper);
    }

    @Test
    void sendsToTargetUserOnly() throws Exception {
        WebSocketSession alice = openSession();
        WebSocketSession bob = openSession();
        when(sessions.findByUserId("bob")).thenReturn(Optional.of(bob));

        broker.sendToUser("bob", OutgoingMessage.delivered("alice", "bob", "hi"));

        verify(bob).sendMessage(any(TextMessage.class));
        verify(alice, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastReachesAllSessions() throws Exception {
        WebSocketSession a = openSession();
        WebSocketSession b = openSession();
        when(sessions.all()).thenReturn(java.util.List.of(a, b));

        broker.broadcast(OutgoingMessage.delivered("system", "*", "hello"));

        verify(a).sendMessage(any(TextMessage.class));
        verify(b).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendToOfflineUserIsNoOp() throws Exception {
        when(sessions.findByUserId("ghost")).thenReturn(Optional.empty());

        broker.sendToUser("ghost", OutgoingMessage.delivered("a", "ghost", "hi"));

        verify(sessions, never()).all();
    }

    @Test
    void doesNotWriteToClosedSession() throws Exception {
        WebSocketSession closed = mock(WebSocketSession.class);
        when(closed.isOpen()).thenReturn(false);
        when(sessions.findByUserId("bob")).thenReturn(Optional.of(closed));

        broker.sendToUser("bob", OutgoingMessage.delivered("a", "bob", "hi"));

        verify(closed, never()).sendMessage(any(TextMessage.class));
    }

    private WebSocketSession openSession() {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.isOpen()).thenReturn(true);
        return s;
    }
}
