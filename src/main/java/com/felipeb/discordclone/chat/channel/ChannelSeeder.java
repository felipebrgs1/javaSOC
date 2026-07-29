package com.felipeb.discordclone.chat.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChannelSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ChannelSeeder.class);
    private static final List<String> DEFAULT_CHANNELS = List.of("general", "random", "dev");

    private final ChannelRepository channels;

    public ChannelSeeder(ChannelRepository channels) {
        this.channels = channels;
    }

    @Override
    public void run(String... args) {
        DEFAULT_CHANNELS.stream()
                .filter(name -> !channels.existsByName(name))
                .map(Channel::new)
                .forEach(channels::save);
        log.info("Seeded channels: {}", channels.findAll());
    }
}
