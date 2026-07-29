package com.felipeb.discordclone.chat.broker;

import org.springframework.web.socket.WebSocketSession;

/**
 * Routes a JSON-serializable payload to the right destination(s).
 * <p>
 * The interface is intentionally payload-agnostic: {@link
 * com.felipeb.discordclone.chat.api.OutgoingMessage}, {@link
 * com.felipeb.discordclone.chat.api.PresenceUpdateMessage} or any future
 * event type flow through the same routing primitives.
 */
public interface MessageBroker {

    void sendToUser(String userId, Object message);

    void broadcast(Object message);

    void publishToChannel(String channelId, Object message);

    /** Direct delivery to a single session. Used for per-recipient messages like initial presence snapshot. */
    void sendToSession(WebSocketSession session, Object message);
}
