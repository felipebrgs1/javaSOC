package com.felipeb.discordclone.chat.channel;

import com.felipeb.discordclone.server.Server;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChannelSeeder {

    private final ChannelRepository channels;

    public ChannelSeeder(ChannelRepository channels) {
        this.channels = channels;
    }

    public void seedDefaults(Server server, List<String> names) {
        for (String name : names) {
            if (!channels.existsByServerAndName(server, name)) {
                channels.save(new Channel(server, name));
            }
        }
    }
}
