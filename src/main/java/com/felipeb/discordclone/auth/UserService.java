package com.felipeb.discordclone.auth;

import com.felipeb.discordclone.chat.channel.ChannelSeeder;
import com.felipeb.discordclone.server.Membership;
import com.felipeb.discordclone.server.MembershipRepository;
import com.felipeb.discordclone.server.Role;
import com.felipeb.discordclone.server.Server;
import com.felipeb.discordclone.server.ServerRepository;
import com.felipeb.discordclone.user.User;
import com.felipeb.discordclone.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;
    private final ServerRepository servers;
    private final MembershipRepository memberships;
    private final ChannelSeeder channelSeeder;
    private final PasswordHasher hasher;
    private final JwtService jwt;

    private final String defaultServerName;
    private final List<String> defaultChannels;

    public UserService(UserRepository users,
                       ServerRepository servers,
                       MembershipRepository memberships,
                       ChannelSeeder channelSeeder,
                       PasswordHasher hasher,
                       JwtService jwt,
                       @Value("${app.bootstrap.default-server}") String defaultServerName,
                       @Value("#{'${app.bootstrap.default-channels}'.split(',')}") List<String> defaultChannels) {
        this.users = users;
        this.servers = servers;
        this.memberships = memberships;
        this.channelSeeder = channelSeeder;
        this.hasher = hasher;
        this.jwt = jwt;
        this.defaultServerName = defaultServerName;
        this.defaultChannels = defaultChannels;
    }

    @Transactional
    public Authenticated register(String username, String email, String password) {
        if (users.existsByUsername(username)) {
            throw new UsernameTakenException(username);
        }
        if (users.existsByEmail(email)) {
            throw new EmailTakenException(email);
        }
        User user;
        try {
            user = users.save(new User(username, email, hasher.hash(password)));
        } catch (DataIntegrityViolationException e) {
            // race: another thread registered the same username/email first
            throw new UsernameTakenException(username);
        }
        ensureMembershipInDefaultServer(user);
        String token = jwt.issue(user.getId(), user.getUsername());
        log.info("Registered user '{}' (id={})", username, user.getId());
        return new Authenticated(user, token);
    }

    @Transactional(readOnly = true)
    public Authenticated login(String username, String password) {
        User user = users.findByUsername(username).orElseThrow(BadCredentialsException::new);
        if (!hasher.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException();
        }
        return new Authenticated(user, jwt.issue(user.getId(), user.getUsername()));
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return users.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return users.findByUsername(username);
    }

    /**
     * Ensures the user is a MEMBER of the default server. ServerBootstrap is
     * responsible for creating the server itself; this just adds the membership.
     */
    private void ensureMembershipInDefaultServer(User user) {
        Server server = servers.findByName(defaultServerName).orElseGet(() -> {
            // Safety net: bootstrap didn't run (e.g. in a sliced test). Create lazily.
            Server created = servers.save(new Server(defaultServerName));
            channelSeeder.seedDefaults(created, defaultChannels);
            log.warn("Default server '{}' was missing — created lazily", defaultServerName);
            return created;
        });
        if (!memberships.existsByUserIdAndServerId(user.getId(), server.getId())) {
            memberships.save(new Membership(user, server, Role.MEMBER));
        }
    }

    public record Authenticated(User user, String token) {
    }
}
