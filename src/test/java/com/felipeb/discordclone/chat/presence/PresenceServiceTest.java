package com.felipeb.discordclone.chat.presence;

import com.felipeb.discordclone.chat.api.PresenceUpdateMessage;
import com.felipeb.discordclone.chat.broker.MessageBroker;
import com.felipeb.discordclone.chat.session.SessionRegistry;
import com.felipeb.discordclone.chat.subscription.ChannelSubscriptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceServiceTest {

    private PresenceRegistry registry;
    private SessionRegistry sessions;
    private ChannelSubscriptions subscriptions;
    private MessageBroker broker;
    private PresenceService presence;

    @BeforeEach
    void setUp() {
        registry = mock(PresenceRegistry.class);
        sessions = mock(SessionRegistry.class);
        subscriptions = mock(ChannelSubscriptions.class);
        broker = mock(MessageBroker.class);
        // 1s idle, 2s offline, deterministic
        presence = new PresenceService(registry, sessions, subscriptions, broker, 1, 2);
    }

    @Test
    void onAuthenticatedMarksOnline() {
        presence.onAuthenticated(1L, "alice");
        verify(registry).markOnline(1L, "alice");
    }

    @Test
    void onHeartbeatRecordsAndStaysOnlineIfWasOnline() {
        when(registry.get(1L)).thenReturn(Optional.of(
                new PresenceInfo(1L, "alice", PresenceStatus.ONLINE, Instant.now())));

        presence.onHeartbeat(1L, "alice");

        verify(registry).recordHeartbeat(1L);
        verify(registry, never()).setStatus(eq(1L), any());
    }

    @Test
    void onHeartbeatAfterIdleTransitionsBackToOnline() {
        when(registry.get(1L)).thenReturn(Optional.of(
                new PresenceInfo(1L, "alice", PresenceStatus.IDLE, Instant.now())));

        presence.onHeartbeat(1L, "alice");

        verify(registry).recordHeartbeat(1L);
        verify(registry).setStatus(1L, PresenceStatus.ONLINE);
    }

    @Test
    void onDisconnectedBroadcastsOfflineToAllChannels() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(subscriptions.channelsOf(session)).thenReturn(List.of("discord-clone:general", "discord-clone:random"));

        presence.onDisconnected(session, 1L, "alice");

        verify(broker).publishToChannel(eq("discord-clone:general"),
                any(PresenceUpdateMessage.class));
        verify(broker).publishToChannel(eq("discord-clone:random"),
                any(PresenceUpdateMessage.class));
        verify(registry).markOffline(1L);
    }

    @Test
    void onSubscribedAnnouncesAndSnapshots() {
        WebSocketSession alice = mock(WebSocketSession.class);
        WebSocketSession bob = mock(WebSocketSession.class);

        Map<String, Object> aliceAttrs = new HashMap<>();
        aliceAttrs.put("userId", 1L);
        aliceAttrs.put("username", "alice");
        when(alice.getAttributes()).thenReturn(aliceAttrs);

        Map<String, Object> bobAttrs = new HashMap<>();
        bobAttrs.put("userId", 2L);
        bobAttrs.put("username", "bob");
        when(bob.getAttributes()).thenReturn(bobAttrs);

        when(registry.get(1L)).thenReturn(Optional.of(
                new PresenceInfo(1L, "alice", PresenceStatus.ONLINE, Instant.now())));
        when(registry.get(2L)).thenReturn(Optional.of(
                new PresenceInfo(2L, "bob", PresenceStatus.IDLE, Instant.now().minusSeconds(60))));

        // bob joins: pre-existing is [alice]
        presence.onSubscribed(bob, 2L, "bob", "c1", List.of(alice));

        // bob gets told about alice (ONLINE) — 1 message
        verify(broker, times(1)).sendToSession(same(bob), any(PresenceUpdateMessage.class));
        // alice gets told about bob (IDLE) — 1 message
        verify(broker, times(1)).sendToSession(same(alice), any(PresenceUpdateMessage.class));
    }

    @Test
    void checkAndTransitionOnlineWithinIdleThresholdStaysOnline() {
        when(registry.get(1L)).thenReturn(Optional.of(
                new PresenceInfo(1L, "alice", PresenceStatus.ONLINE, Instant.now())));

        presence.checkAndTransition(1L);

        verify(registry, never()).setStatus(eq(1L), any());
    }

    @Test
    void checkAndTransitionAfterIdleThresholdGoesIdle() throws InterruptedException {
        // 1500ms ago: > 1s (idle) and < 2s (offline) with the test thresholds
        when(registry.get(1L)).thenReturn(Optional.of(
                new PresenceInfo(1L, "alice", PresenceStatus.ONLINE, Instant.now().minusMillis(1500))));

        presence.checkAndTransition(1L);

        verify(registry).setStatus(1L, PresenceStatus.IDLE);
    }

    @Test
    void checkAndTransitionAfterOfflineThresholdGoesOffline() throws InterruptedException {
        when(registry.get(1L)).thenReturn(Optional.of(
                new PresenceInfo(1L, "alice", PresenceStatus.IDLE, Instant.now().minusSeconds(5))));

        presence.checkAndTransition(1L);

        verify(registry).setStatus(1L, PresenceStatus.OFFLINE);
    }

    @Test
    void checkAndTransitionDoesNothingIfAlreadyOffline() {
        when(registry.get(1L)).thenReturn(Optional.of(
                new PresenceInfo(1L, "alice", PresenceStatus.OFFLINE, Instant.now().minusSeconds(60))));

        presence.checkAndTransition(1L);

        verify(registry, never()).setStatus(eq(1L), any());
    }
}
