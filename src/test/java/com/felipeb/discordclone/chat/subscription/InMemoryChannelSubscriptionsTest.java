package com.felipeb.discordclone.chat.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryChannelSubscriptionsTest {

    private InMemoryChannelSubscriptions subs;
    private WebSocketSession alice;
    private WebSocketSession bob;
    private WebSocketSession carol;

    @BeforeEach
    void setUp() {
        subs = new InMemoryChannelSubscriptions();
        alice = open();
        bob = open();
        carol = open();
    }

    @Test
    void firstSubscribeSeesEmptyPreExisting() {
        Collection<WebSocketSession> pre = subs.subscribeAndGetPreExisting("general", alice);
        assertThat(pre).isEmpty();
        assertThat(subs.isSubscribed("general", alice)).isTrue();
        assertThat(subs.subscribersOf("general")).containsExactly(alice);
    }

    @Test
    void secondSubscribeSeesFirstInPreExisting() {
        subs.subscribeAndGetPreExisting("general", alice);
        Collection<WebSocketSession> pre = subs.subscribeAndGetPreExisting("general", bob);

        assertThat(pre).containsExactly(alice);
        assertThat(subs.subscribersOf("general")).containsExactlyInAnyOrder(alice, bob);
    }

    @Test
    void sameSessionAcrossChannels() {
        subs.subscribeAndGetPreExisting("general", alice);
        subs.subscribeAndGetPreExisting("dev", alice);

        assertThat(subs.subscribersOf("general")).containsExactly(alice);
        assertThat(subs.subscribersOf("dev")).containsExactly(alice);
    }

    @Test
    void unsubscribeRemovesSession() {
        subs.subscribeAndGetPreExisting("general", alice);
        subs.subscribeAndGetPreExisting("general", bob);
        subs.unsubscribe("general", alice);

        assertThat(subs.isSubscribed("general", alice)).isFalse();
        assertThat(subs.subscribersOf("general")).containsExactly(bob);
    }

    @Test
    void unsubscribeFromAllRemovesFromEveryChannel() {
        subs.subscribeAndGetPreExisting("general", alice);
        subs.subscribeAndGetPreExisting("dev", alice);
        subs.subscribeAndGetPreExisting("random", bob);

        subs.unsubscribeFromAll(alice);

        assertThat(subs.isSubscribed("general", alice)).isFalse();
        assertThat(subs.isSubscribed("dev", alice)).isFalse();
        assertThat(subs.isSubscribed("random", bob)).isTrue();
    }

    @Test
    void emptyChannelSetIsCleanedUp() {
        subs.subscribeAndGetPreExisting("general", alice);
        subs.unsubscribe("general", alice);

        assertThat(subs.subscribersOf("general")).isEmpty();
        assertThat(subs.isSubscribed("general", alice)).isFalse();
    }

    @Test
    void unsubscribedFromUnknownChannelIsNoOp() {
        subs.unsubscribe("ghost", alice);
        assertThat(subs.isSubscribed("ghost", alice)).isFalse();
    }

    @Test
    void subscribersOfUnknownChannelIsEmpty() {
        assertThat(subs.subscribersOf("ghost")).isEmpty();
    }

    @Test
    void channelsOfReturnsAllChannelsForASession() {
        subs.subscribeAndGetPreExisting("general", alice);
        subs.subscribeAndGetPreExisting("dev", alice);
        subs.subscribeAndGetPreExisting("general", bob);

        assertThat(subs.channelsOf(alice)).containsExactlyInAnyOrder("general", "dev");
        assertThat(subs.channelsOf(bob)).containsExactly("general");
        assertThat(subs.channelsOf(carol)).isEmpty();
    }

    private WebSocketSession open() {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.isOpen()).thenReturn(true);
        return s;
    }
}
