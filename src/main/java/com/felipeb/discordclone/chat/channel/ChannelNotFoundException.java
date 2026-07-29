package com.felipeb.discordclone.chat.channel;

public class ChannelNotFoundException extends RuntimeException {
    public ChannelNotFoundException(String name) {
        super("Channel not found: " + name);
    }
}
