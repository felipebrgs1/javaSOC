package com.felipeb.discordclone.chat.channel;

public class MessageNotFoundException extends RuntimeException {
    public MessageNotFoundException(Long id) {
        super("Message not found: " + id);
    }
}
