package com.felipeb.discordclone.chat.presence;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically asks {@link PresenceService} to evaluate each known user
 * and transition ONLINE → IDLE → OFFLINE based on time since last heartbeat.
 */
@Component
public class PresenceScheduler {

    private final PresenceService presence;
    private final PresenceRegistry registry;

    public PresenceScheduler(PresenceService presence, PresenceRegistry registry) {
        this.presence = presence;
        this.registry = registry;
    }

    @Scheduled(fixedDelayString = "${app.presence.check-interval-ms:5000}")
    public void tick() {
        for (Long userId : registry.allUserIds()) {
            presence.checkAndTransition(userId);
        }
    }
}
