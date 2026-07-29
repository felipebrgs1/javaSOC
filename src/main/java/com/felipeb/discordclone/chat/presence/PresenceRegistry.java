package com.felipeb.discordclone.chat.presence;

import java.util.Collection;
import java.util.Optional;

public interface PresenceRegistry {

    void markOnline(Long userId, String username);

    void recordHeartbeat(Long userId);

    void setStatus(Long userId, PresenceStatus status);

    void markOffline(Long userId);

    Optional<PresenceInfo> get(Long userId);

    Collection<Long> allUserIds();
}
