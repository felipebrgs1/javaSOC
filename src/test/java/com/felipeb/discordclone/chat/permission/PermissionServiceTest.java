package com.felipeb.discordclone.chat.permission;

import com.felipeb.discordclone.chat.channel.Channel;
import com.felipeb.discordclone.chat.channel.ChannelPermission;
import com.felipeb.discordclone.server.Membership;
import com.felipeb.discordclone.server.MembershipRepository;
import com.felipeb.discordclone.server.Role;
import com.felipeb.discordclone.server.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionServiceTest {

    private MembershipRepository memberships;
    private PermissionService permissions;

    @BeforeEach
    void setUp() {
        memberships = mock(MembershipRepository.class);
        permissions = new PermissionService(memberships);
    }

    @Test
    void memberCanReadAndWrite() {
        Membership m = new Membership(null, null, Role.MEMBER);
        when(memberships.findByUserIdAndServerId(1L, 10L)).thenReturn(Optional.of(m));

        assertThat(permissions.requireReadAccess(1L, 10L).getRole()).isEqualTo(Role.MEMBER);
        assertThat(permissions.requireWriteAccess(1L, 10L).getRole()).isEqualTo(Role.MEMBER);
    }

    @Test
    void nonMemberCannotRead() {
        when(memberships.findByUserIdAndServerId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissions.requireReadAccess(99L, 10L))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    void nonMemberCannotWrite() {
        when(memberships.findByUserIdAndServerId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissions.requireWriteAccess(99L, 10L))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void memberCannotManageChannels() {
        Membership m = new Membership(null, null, Role.MEMBER);
        when(memberships.findByUserIdAndServerId(1L, 10L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> permissions.requireManageChannels(1L, 10L))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("ADMIN");
    }

    @Test
    void adminCanManageChannels() {
        Membership m = new Membership(null, null, Role.ADMIN);
        when(memberships.findByUserIdAndServerId(1L, 10L)).thenReturn(Optional.of(m));

        assertThat(permissions.requireManageChannels(1L, 10L).getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void adminCannotManageServer() {
        Membership m = new Membership(null, null, Role.ADMIN);
        when(memberships.findByUserIdAndServerId(1L, 10L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> permissions.requireManageServer(1L, 10L))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("OWNER");
    }

    @Test
    void ownerCanManageServer() {
        Membership m = new Membership(null, null, Role.OWNER);
        when(memberships.findByUserIdAndServerId(1L, 10L)).thenReturn(Optional.of(m));

        assertThat(permissions.requireManageServer(1L, 10L).getRole()).isEqualTo(Role.OWNER);
    }

    // ---- channel-level write ----

    @Test
    void memberCanPostInReadWriteChannel() {
        Server server = new Server("s1");
        Channel channel = new Channel(server, "general", ChannelPermission.READ_WRITE);
        Membership m = new Membership(null, server, Role.MEMBER);
        when(memberships.findByUserIdAndServerId(1L, server.getId())).thenReturn(Optional.of(m));

        assertThat(permissions.requireChannelWriteAccess(1L, channel).getRole()).isEqualTo(Role.MEMBER);
    }

    @Test
    void memberCannotPostInReadOnlyChannel() {
        Server server = new Server("s1");
        Channel channel = new Channel(server, "announcements", ChannelPermission.READ_ONLY);
        Membership m = new Membership(null, server, Role.MEMBER);
        when(memberships.findByUserIdAndServerId(1L, server.getId())).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> permissions.requireChannelWriteAccess(1L, channel))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void adminCanAlwaysPostInReadOnlyChannel() {
        Server server = new Server("s1");
        Channel channel = new Channel(server, "announcements", ChannelPermission.READ_ONLY);
        Membership m = new Membership(null, server, Role.ADMIN);
        when(memberships.findByUserIdAndServerId(1L, server.getId())).thenReturn(Optional.of(m));

        assertThat(permissions.requireChannelWriteAccess(1L, channel).getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void ownerCanAlwaysPostInReadOnlyChannel() {
        Server server = new Server("s1");
        Channel channel = new Channel(server, "announcements", ChannelPermission.READ_ONLY);
        Membership m = new Membership(null, server, Role.OWNER);
        when(memberships.findByUserIdAndServerId(1L, server.getId())).thenReturn(Optional.of(m));

        assertThat(permissions.requireChannelWriteAccess(1L, channel).getRole()).isEqualTo(Role.OWNER);
    }

    @Test
    void nonMemberCannotPostEvenInReadWriteChannel() {
        Server server = new Server("s1");
        Channel channel = new Channel(server, "general", ChannelPermission.READ_WRITE);
        when(memberships.findByUserIdAndServerId(99L, server.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissions.requireChannelWriteAccess(99L, channel))
                .isInstanceOf(PermissionDeniedException.class);
    }
}
