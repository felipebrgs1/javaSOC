package com.felipeb.discordclone.chat.presence;

import com.felipeb.discordclone.chat.api.PresenceUpdateMessage;
import com.felipeb.discordclone.chat.broker.MessageBroker;
import com.felipeb.discordclone.chat.session.SessionRegistry;
import com.felipeb.discordclone.chat.subscription.ChannelSubscriptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/**
 * Orchestrates presence transitions and broadcasts PRESENCE_UPDATED to the
 * channels a user is currently subscribed to. The scheduler calls
 * {@link #checkAndTransition(Long)} periodically; the handler calls the
 * {@code onX} methods on auth/heartbeat/disconnect/subscribe.
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private final PresenceRegistry registry;
    private final SessionRegistry sessions;
    private final ChannelSubscriptions subscriptions;
    private final MessageBroker broker;

    private final long idleThresholdMs;
    private final long offlineThresholdMs;

    public PresenceService(PresenceRegistry registry,
                           SessionRegistry sessions,
                           ChannelSubscriptions subscriptions,
                           MessageBroker broker,
                           @Value("${app.presence.heartbeat-idle-seconds:30}") long idleSeconds,
                           @Value("${app.presence.heartbeat-offline-seconds:60}") long offlineSeconds) {
        this.registry = registry;
        this.sessions = sessions;
        this.subscriptions = subscriptions;
        this.broker = broker;
        this.idleThresholdMs = idleSeconds * 1000L;
        this.offlineThresholdMs = offlineSeconds * 1000L;
    }

    // ---------- called from the WebSocket handler ----------

    public void onAuthenticated(Long userId, String username) {
        registry.markOnline(userId, username);
        log.info("Presence: user '{}' is ONLINE", username);
    }

    public void onHeartbeat(Long userId, String username) {
        Optional<PresenceInfo> before = registry.get(userId);
        registry.recordHeartbeat(userId);
        if (before.isPresent() && before.get().status() != PresenceStatus.ONLINE) {
            // Was IDLE or OFFLINE; a heartbeat proves they're back
            registry.setStatus(userId, PresenceStatus.ONLINE);
            broadcastToUserChannels(userId, username, PresenceStatus.ONLINE);
            log.info("Presence: user '{}' is back ONLINE (heartbeat, was {})",
                    username, before.get().status());
        }
    }

    public void onDisconnected(WebSocketSession session, Long userId, String username) {
        for (String channel : subscriptions.channelsOf(session)) {
            broker.publishToChannel(channel, PresenceUpdateMessage.of(userId, username, PresenceStatus.OFFLINE));
        }
        registry.markOffline(userId);
        log.info("Presence: user '{}' is OFFLINE (disconnect)", username);
    }

    /**
     * Called after a successful SUBSCRIBE.
     * <p>
     * {@code preExisting} is the snapshot of the channel's subscribers taken
     * BEFORE the new session was added — critical to avoid the race where two
     * concurrent subscribes see each other and double-announce.
     */
    public void onSubscribed(WebSocketSession newSession,
                             Long newUserId,
                             String newUsername,
                             String channelKey,
                             Collection<WebSocketSession> preExisting) {
        PresenceStatus myStatus = registry.get(newUserId).map(PresenceInfo::status).orElse(PresenceStatus.ONLINE);

        // 1) Tell pre-existing subscribers that the new one just joined
        for (WebSocketSession other : preExisting) {
            broker.sendToSession(other, PresenceUpdateMessage.of(newUserId, newUsername, myStatus));
        }

        // 2) Tell the new subscriber the status of those already in the channel
        for (WebSocketSession other : preExisting) {
            Object otherId = other.getAttributes().get("userId");
            Object otherName = other.getAttributes().get("username");
            if (otherId instanceof Long oid && otherName instanceof String oname) {
                PresenceStatus otherStatus = registry.get(oid).map(PresenceInfo::status).orElse(PresenceStatus.ONLINE);
                broker.sendToSession(newSession, PresenceUpdateMessage.of(oid, oname, otherStatus));
            }
        }
    }

    // ---------- called from the scheduler ----------

    public void checkAndTransition(Long userId) {
        PresenceInfo info = registry.get(userId).orElse(null);
        if (info == null || info.status() == PresenceStatus.OFFLINE) {
            return; // nothing to do; OFFLINE is terminal until next AUTH
        }
        long elapsed = Duration.between(info.lastSeen(), Instant.now()).toMillis();
        PresenceStatus next;
        if (elapsed >= offlineThresholdMs)      next = PresenceStatus.OFFLINE;
        else if (elapsed >= idleThresholdMs)    next = PresenceStatus.IDLE;
        else                                    next = PresenceStatus.ONLINE;

        if (next != info.status()) {
            registry.setStatus(userId, next);
            broadcastToUserChannels(userId, info.username(), next);
            log.info("Presence: user '{}' is now {} (elapsed={}s)", info.username(), next, elapsed / 1000);
        }
    }

    // ---------- helpers ----------

    private void broadcastToUserChannels(Long userId, String username, PresenceStatus status) {
        sessions.findByUserId(username).ifPresent(session -> {
            for (String channel : subscriptions.channelsOf(session)) {
                broker.publishToChannel(channel, PresenceUpdateMessage.of(userId, username, status));
            }
        });
    }
}
