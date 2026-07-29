package com.felipeb.discordclone.chat.permission;

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

    /**
     * Returns the user's membership in the server, or throws if they're not a member.
     * In Phase 3, every member can read and write to every channel in the server.
     * Per-channel overwrites land in Phase 5.
     */
    @Transactional(readOnly = true)
    public Membership requireReadAccess(Long userId, Long serverId) {
        return memberships.findByUserIdAndServerId(userId, serverId)
                .orElseThrow(() -> new PermissionDeniedException(
                        "User is not a member of server " + serverId));
    }

    @Transactional(readOnly = true)
    public Membership requireWriteAccess(Long userId, Long serverId) {
        // Phase 3: same as read. Phase 5 will add per-channel overwrites.
        return requireReadAccess(userId, serverId);
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
