package com.felipeb.discordclone.chat.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felipeb.discordclone.chat.api.OutgoingMessage;
import com.felipeb.discordclone.chat.session.SessionRegistry;
import com.felipeb.discordclone.chat.subscription.ChannelSubscriptions;
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
    private ChannelSubscriptions subscriptions;
    private InMemoryMessageBroker broker;

    @BeforeEach
    void setUp() {
        sessions = mock(SessionRegistry.class);
        subscriptions = mock(ChannelSubscriptions.class);
        // findAndRegisterModules() mimics Spring Boot's auto-configured ObjectMapper
        // so the JavaTimeModule (jackson-datatype-jsr310) is picked up.
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        broker = new InMemoryMessageBroker(sessions, subscriptions, mapper);
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

    @Test
    void publishToChannelReachesOnlyChannelSubscribers() throws Exception {
        WebSocketSession alice = openSession();
        WebSocketSession bob = openSession();
        WebSocketSession carol = openSession();
        when(subscriptions.subscribersOf("general")).thenReturn(java.util.List.of(alice, bob));

        broker.publishToChannel("general", OutgoingMessage.published(1L, "alice", "general", "hi", java.time.Instant.now()));

        verify(alice).sendMessage(any(TextMessage.class));
        verify(bob).sendMessage(any(TextMessage.class));
        verify(carol, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void publishToUnknownChannelIsNoOp() throws Exception {
        when(subscriptions.subscribersOf("ghost")).thenReturn(java.util.Collections.emptyList());

        broker.publishToChannel("ghost", OutgoingMessage.published(1L, "a", "ghost", "hi", java.time.Instant.now()));

        // No sessions to verify, but the call must not throw and not touch the session registry
        verify(sessions, never()).all();
        verify(sessions, never()).findByUserId(any());
    }

    private WebSocketSession openSession() {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.isOpen()).thenReturn(true);
        return s;
    }
}
