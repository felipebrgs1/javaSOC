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
    // voice signaling (client -> server)
    VOICE_JOIN,
    VOICE_LEAVE,
    SDP_OFFER,
    SDP_ANSWER,
    ICE_CANDIDATE,
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
    // voice signaling (server -> client)
    VOICE_JOINED,
    VOICE_USER_JOINED,
    VOICE_USER_LEFT,
    ERROR
}
