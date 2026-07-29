package com.felipeb.discordclone.chat.permission;

import com.felipeb.discordclone.chat.channel.Channel;
import com.felipeb.discordclone.chat.channel.ChannelPermission;
import com.felipeb.discordclone.server.Membership;
import com.felipeb.discordclone.server.MembershipRepository;
import com.felipeb.discordclone.server.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {

    private final MembershipRepository memberships;

    public PermissionService(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    /** Returns the user's membership in the server, or throws if they're not a member. */
    @Transactional(readOnly = true)
    public Membership requireReadAccess(Long userId, Long serverId) {
        return memberships.findByUserIdAndServerId(userId, serverId)
                .orElseThrow(() -> new PermissionDeniedException(
                        "User is not a member of server " + serverId));
    }

    /** Server-level write = member of the server. */
    @Transactional(readOnly = true)
    public Membership requireWriteAccess(Long userId, Long serverId) {
        return requireReadAccess(userId, serverId);
    }

    /**
     * Channel-level write: ADMIN+ can always post. MEMBERs can only post if the
     * channel's default permission is READ_WRITE.
     */
    @Transactional(readOnly = true)
    public Membership requireChannelWriteAccess(Long userId, Channel channel) {
        Membership m = requireReadAccess(userId, channel.getServer().getId());
        if (m.getRole() == Role.MEMBER && channel.getDefaultPermission() == ChannelPermission.READ_ONLY) {
            throw new PermissionDeniedException(
                    "#" + channel.getName() + " is read-only for your role");
        }
        return m;
    }

    @Transactional(readOnly = true)
    public Membership requireManageChannels(Long userId, Long serverId) {
        Membership m = requireReadAccess(userId, serverId);
        if (m.getRole() == Role.MEMBER) {
            throw new PermissionDeniedException("Requires ADMIN or OWNER");
        }
        return m;
    }

    @Transactional(readOnly = true)
    public Membership requireManageServer(Long userId, Long serverId) {
        Membership m = requireReadAccess(userId, serverId);
        if (m.getRole() != Role.OWNER) {
            throw new PermissionDeniedException("Requires OWNER");
        }
        return m;
    }
}
