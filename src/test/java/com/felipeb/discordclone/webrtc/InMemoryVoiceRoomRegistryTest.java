package com.felipeb.discordclone.webrtc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryVoiceRoomRegistryTest {

    private InMemoryVoiceRoomRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryVoiceRoomRegistry();
    }

    @Test
    void firstJoinSeesNoPreExisting() {
        assertThat(registry.join("s1:general", "alice")).isEmpty();
        assertThat(registry.participants("s1:general")).containsExactly("alice");
        assertThat(registry.isInRoom("s1:general", "alice")).isTrue();
    }

    @Test
    void secondJoinSeesFirstInPreExisting() {
        registry.join("s1:general", "alice");

        assertThat(registry.join("s1:general", "bob")).containsExactly("alice");
        assertThat(registry.participants("s1:general"))
                .containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void leaveRemovesUserAndReturnsRemaining() {
        registry.join("s1:general", "alice");
        registry.join("s1:general", "bob");

        assertThat(registry.leave("s1:general", "alice")).containsExactly("bob");
        assertThat(registry.participants("s1:general")).containsExactly("bob");
        assertThat(registry.isInRoom("s1:general", "alice")).isFalse();
    }

    @Test
    void leaveLastUserCleansUpRoom() {
        registry.join("s1:general", "alice");
        registry.leave("s1:general", "alice");

        assertThat(registry.participants("s1:general")).isEmpty();
        assertThat(registry.leave("s1:general", "alice")).isEmpty();
    }

    @Test
    void leaveUnknownRoomIsNoOp() {
        assertThat(registry.leave("ghost", "alice")).isEmpty();
        assertThat(registry.isInRoom("ghost", "alice")).isFalse();
    }

    @Test
    void joinMovesUserBetweenRooms() {
        registry.join("s1:general", "alice");
        registry.join("s1:dev", "bob");

        // alice moves to dev; she is no longer in general
        assertThat(registry.join("s1:dev", "alice")).containsExactly("bob");
        assertThat(registry.isInRoom("s1:general", "alice")).isFalse();
        assertThat(registry.isInRoom("s1:dev", "alice")).isTrue();
        assertThat(registry.participants("s1:general")).isEmpty();
    }

    @Test
    void leaveAllRemovesFromEveryRoom() {
        registry.join("s1:general", "alice");
        registry.join("s1:dev", "bob");

        assertThat(registry.leaveAll("alice")).containsExactly("s1:general");
        assertThat(registry.leaveAll("bob")).containsExactly("s1:dev");
        assertThat(registry.participants("s1:general")).isEmpty();
        assertThat(registry.participants("s1:dev")).isEmpty();
    }

    @Test
    void leaveAllOnUnknownUserIsNoOp() {
        assertThat(registry.leaveAll("ghost")).isEmpty();
    }

    @Test
    void participantsOfUnknownRoomIsEmpty() {
        assertThat(registry.participants("ghost")).isEmpty();
        assertThat(registry.isInRoom("ghost", "alice")).isFalse();
    }

    @Test
    void emptyRoomIsCleanedUpAfterLeaveAll() {
        registry.join("s1:general", "alice");
        registry.leaveAll("alice");
        registry.join("s1:general", "bob");
        assertThat(registry.participants("s1:general")).containsExactly("bob");
    }
}
