package com.felipeb.discordclone.chat.subscription;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryChannelSubscriptions implements ChannelSubscriptions {

    private final Map<String, Set<WebSocketSession>> byChannel = new ConcurrentHashMap<>();

    @Override
    public Collection<WebSocketSession> subscribeAndGetPreExisting(String channelId, WebSocketSession session) {
        Set<WebSocketSession> set = byChannel.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet());
        // Synchronize on the set so the snapshot+add is atomic for this channel.
        synchronized (set) {
            Collection<WebSocketSession> preExisting = new ArrayList<>(set);
            set.add(session);
            return preExisting;
        }
    }

    @Override
    public void unsubscribe(String channelId, WebSocketSession session) {
        Set<WebSocketSession> set = byChannel.get(channelId);
        if (set != null) {
            set.remove(session);
        }
    }

    @Override
    public void unsubscribeFromAll(WebSocketSession session) {
        byChannel.forEach((id, set) -> set.remove(session));
        byChannel.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    @Override
    public boolean isSubscribed(String channelId, WebSocketSession session) {
        Set<WebSocketSession> set = byChannel.get(channelId);
        return set != null && set.contains(session);
    }

    @Override
    public Collection<WebSocketSession> subscribersOf(String channelId) {
        Set<WebSocketSession> set = byChannel.get(channelId);
        return set != null ? set : Collections.emptySet();
    }

    @Override
    public Collection<String> channelsOf(WebSocketSession session) {
        return byChannel.entrySet().stream()
                .filter(e -> e.getValue().contains(session))
                .map(Map.Entry::getKey)
                .toList();
    }
}
