package com.felipeb.discordclone.chat.api;

public enum MessageType {
    // client -> server
    AUTH,
    HEARTBEAT,
    DIRECT_MESSAGE,
    SUBSCRIBE,
    UNSUBSCRIBE,
    CHANNEL_MESSAGE,
    EDIT_MESSAGE,
    DELETE_MESSAGE,
    REACT,
    UNREACT,
    // server -> client
    AUTHENTICATED,
    DELIVERED,
    PUBLISHED,
    SUBSCRIBED,
    UNSUBSCRIBED,
    HISTORY,
    PRESENCE_UPDATED,
    MESSAGE_EDITED,
    MESSAGE_DELETED,
    REACTION_ADDED,
    REACTION_REMOVED,
    ERROR
}
