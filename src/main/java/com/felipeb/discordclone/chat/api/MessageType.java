package com.felipeb.discordclone.chat.api;

public enum MessageType {
    // client -> server
    AUTH,
    HEARTBEAT,
    DIRECT_MESSAGE,
    SUBSCRIBE,
    UNSUBSCRIBE,
    CHANNEL_MESSAGE,
    // server -> client
    AUTHENTICATED,
    DELIVERED,
    PUBLISHED,
    SUBSCRIBED,
    UNSUBSCRIBED,
    HISTORY,
    PRESENCE_UPDATED,
    ERROR
}
