package com.felipeb.discordclone.chat.api;

public enum MessageType {
    // client -> server
    CONNECT,
    DIRECT_MESSAGE,
    SUBSCRIBE,
    UNSUBSCRIBE,
    CHANNEL_MESSAGE,
    // server -> client
    DELIVERED,
    PUBLISHED,
    SUBSCRIBED,
    UNSUBSCRIBED,
    HISTORY,
    ERROR
}
