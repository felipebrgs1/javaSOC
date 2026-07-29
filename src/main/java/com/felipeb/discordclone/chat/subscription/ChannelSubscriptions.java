package com.felipeb.discordclone.chat.subscription;

import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;

/**
 * Tracks which WebSocket sessions are subscribed to which channels.
 * In-memory now; will become Redis pub/sub-backed when we scale horizontally.
 */
public interface ChannelSubscriptions {

    /**
     * Atomically capture the current subscribers of {@code channelId} and
     * add {@code session} to it. Returns the pre-existing subscribers
     * (excluding {@code session}). This avoids the race where two concurrent
     * subscribes both observe an empty channel.
     */
    Collection<WebSocketSession> subscribeAndGetPreExisting(String channelId, WebSocketSession session);

    void unsubscribe(String channelId, WebSocketSession session);

    void unsubscribeFromAll(WebSocketSession session);

    boolean isSubscribed(String channelId, WebSocketSession session);

    Collection<WebSocketSession> subscribersOf(String channelId);

    /** Returns the channels (by id, e.g. "server:channel") the session is currently in. */
    Collection<String> channelsOf(WebSocketSession session);
}
