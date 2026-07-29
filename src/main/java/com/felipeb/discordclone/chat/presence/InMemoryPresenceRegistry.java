package com.felipeb.discordclone.chat.presence;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryPresenceRegistry implements PresenceRegistry {

    private final Map<Long, PresenceInfo> byUser = new ConcurrentHashMap<>();

    @Override
    public void markOnline(Long userId, String username) {
        // Replacing an existing entry handles reconnection cleanly
        byUser.put(userId, new PresenceInfo(userId, username, PresenceStatus.ONLINE, Instant.now()));
    }

    @Override
    public void recordHeartbeat(Long userId) {
        byUser.computeIfPresent(userId, (id, prev) ->
                new PresenceInfo(id, prev.username(), prev.status(), Instant.now()));
    }

    @Override
    public void setStatus(Long userId, PresenceStatus status) {
        byUser.computeIfPresent(userId, (id, prev) ->
                new PresenceInfo(id, prev.username(), status, prev.lastSeen()));
    }

    @Override
    public void markOffline(Long userId) {
        byUser.computeIfPresent(userId, (id, prev) ->
                new PresenceInfo(id, prev.username(), PresenceStatus.OFFLINE, prev.lastSeen()));
    }

    @Override
    public Optional<PresenceInfo> get(Long userId) {
        return Optional.ofNullable(byUser.get(userId));
    }

    @Override
    public Collection<Long> allUserIds() {
        return byUser.keySet();
    }
}
