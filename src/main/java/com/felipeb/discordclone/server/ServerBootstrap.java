package com.felipeb.discordclone.server;

import com.felipeb.discordclone.auth.PasswordHasher;
import com.felipeb.discordclone.chat.channel.ChannelSeeder;
import com.felipeb.discordclone.chat.channel.ChannelType;
import com.felipeb.discordclone.user.User;
import com.felipeb.discordclone.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Ensures the default "discord-clone" server exists on first boot,
 * owned by a non-login "system" user. New users are auto-joined as MEMBER
 * (see UserService#register).
 */
@Component
public class ServerBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ServerBootstrap.class);
    private static final String SYSTEM_USERNAME = "system";
    private static final String SYSTEM_EMAIL = "system@discord-clone.local";

    private final ServerRepository servers;
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final ChannelSeeder channelSeeder;
    private final PasswordHasher hasher;

    private final String defaultServerName;
    private final List<String> defaultChannels;
    private final List<String> defaultVoiceChannels;

    public ServerBootstrap(ServerRepository servers,
                           UserRepository users,
                           MembershipRepository memberships,
                           ChannelSeeder channelSeeder,
                           PasswordHasher hasher,
                           @Value("${app.bootstrap.default-server}") String defaultServerName,
                           @Value("#{'${app.bootstrap.default-channels}'.split(',')}") List<String> defaultChannels,
                           @Value("#{'${app.bootstrap.default-voice-channels}'.split(',')}") List<String> defaultVoiceChannels) {
        this.servers = servers;
        this.users = users;
        this.memberships = memberships;
        this.channelSeeder = channelSeeder;
        this.hasher = hasher;
        this.defaultServerName = defaultServerName;
        this.defaultChannels = defaultChannels;
        this.defaultVoiceChannels = defaultVoiceChannels;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (servers.existsByName(defaultServerName)) {
            log.info("Default server '{}' already present", defaultServerName);
            return;
        }
        User system = users.findByUsername(SYSTEM_USERNAME).orElseGet(() ->
                users.save(new User(SYSTEM_USERNAME, SYSTEM_EMAIL, hasher.hash(UUID.randomUUID().toString())))
        );
        Server server = servers.save(new Server(defaultServerName));
        memberships.save(new Membership(system, server, Role.OWNER));
        channelSeeder.seedDefaults(server, defaultChannels, ChannelType.TEXT);
        channelSeeder.seedDefaults(server, defaultVoiceChannels, ChannelType.VOICE);
        log.info("Bootstrapped default server '{}' (id={}) owned by '{}'", server.getName(), server.getId(), SYSTEM_USERNAME);
    }
}
