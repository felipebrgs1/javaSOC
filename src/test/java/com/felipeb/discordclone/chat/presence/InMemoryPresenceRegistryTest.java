package com.felipeb.discordclone.chat.presence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPresenceRegistryTest {

    private InMemoryPresenceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryPresenceRegistry();
    }

    @Test
    void markOnlineAddsUser() {
        registry.markOnline(1L, "alice");

        assertThat(registry.get(1L)).isPresent();
        assertThat(registry.get(1L).get().status()).isEqualTo(PresenceStatus.ONLINE);
        assertThat(registry.get(1L).get().username()).isEqualTo("alice");
    }

    @Test
    void markOnlineTwiceResetsLastSeen() throws InterruptedException {
        registry.markOnline(1L, "alice");
        var firstSeen = registry.get(1L).get().lastSeen();
        Thread.sleep(5);
        registry.markOnline(1L, "alice");
        var secondSeen = registry.get(1L).get().lastSeen();
        assertThat(secondSeen).isAfter(firstSeen);
    }

    @Test
    void recordHeartbeatUpdatesLastSeenButKeepsStatus() throws InterruptedException {
        registry.markOnline(1L, "alice");
        var firstSeen = registry.get(1L).get().lastSeen();
        Thread.sleep(5);
        registry.recordHeartbeat(1L);
        var secondSeen = registry.get(1L).get().lastSeen();
        assertThat(secondSeen).isAfter(firstSeen);
        assertThat(registry.get(1L).get().status()).isEqualTo(PresenceStatus.ONLINE);
    }

    @Test
    void setStatusChangesStatusPreservingOtherFields() {
        registry.markOnline(1L, "alice");
        var before = registry.get(1L).get();
        registry.setStatus(1L, PresenceStatus.IDLE);
        var after = registry.get(1L).get();
        assertThat(after.status()).isEqualTo(PresenceStatus.IDLE);
        assertThat(after.lastSeen()).isEqualTo(before.lastSeen());
        assertThat(after.username()).isEqualTo("alice");
    }

    @Test
    void markOfflineTransitionsToOffline() {
        registry.markOnline(1L, "alice");
        registry.markOffline(1L);
        assertThat(registry.get(1L).get().status()).isEqualTo(PresenceStatus.OFFLINE);
    }

    @Test
    void recordHeartbeatOnMissingUserIsNoOp() {
        registry.recordHeartbeat(99L);
        assertThat(registry.get(99L)).isEmpty();
    }

    @Test
    void setStatusOnMissingUserIsNoOp() {
        registry.setStatus(99L, PresenceStatus.OFFLINE);
        assertThat(registry.get(99L)).isEmpty();
    }

    @Test
    void allUserIdsListsEveryUser() {
        registry.markOnline(1L, "alice");
        registry.markOnline(2L, "bob");
        registry.markOffline(3L);  // creates no entry

        assertThat(registry.allUserIds()).containsExactlyInAnyOrder(1L, 2L);
    }
}
