package com.felipeb.discordclone.chat.subscription;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryChannelSubscriptions implements ChannelSubscriptions {

    private final Map<String, Set<WebSocketSession>> byChannel = new ConcurrentHashMap<>();

    @Override
    public void subscribe(String channelId, WebSocketSession session) {
        byChannel.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(session);
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
}
