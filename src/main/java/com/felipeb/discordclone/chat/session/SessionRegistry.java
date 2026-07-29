package com.felipeb.discordclone.chat.session;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which user is currently bound to which WebSocket session.
 * <p>
 * Pure in-memory. When we add multiple server instances this becomes
 * a Redis-backed registry (Phase 3+).
 */
@Component
public class SessionRegistry {

    private final Map<String, WebSocketSession> sessionsByUserId = new ConcurrentHashMap<>();

    public void register(String userId, WebSocketSession session) {
        sessionsByUserId.put(userId, session);
    }

    public void unregister(WebSocketSession session) {
        sessionsByUserId.entrySet().removeIf(entry -> entry.getValue().equals(session));
    }

    public Optional<WebSocketSession> findByUserId(String userId) {
        return Optional.ofNullable(sessionsByUserId.get(userId));
    }

    public boolean isOnline(String userId) {
        WebSocketSession session = sessionsByUserId.get(userId);
        return session != null && session.isOpen();
    }

    public Collection<WebSocketSession> all() {
        return sessionsByUserId.values();
    }
}
