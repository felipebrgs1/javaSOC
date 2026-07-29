package com.felipeb.discordclone.chat.reaction;

import com.felipeb.discordclone.chat.channel.Channel;
import com.felipeb.discordclone.chat.channel.ChannelRepository;
import com.felipeb.discordclone.chat.channel.Message;
import com.felipeb.discordclone.chat.channel.MessageRepository;
import com.felipeb.discordclone.server.Server;
import com.felipeb.discordclone.server.ServerRepository;
import com.felipeb.discordclone.user.User;
import com.felipeb.discordclone.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class ReactionRepositoryTest {

    @Autowired UserRepository users;
    @Autowired ServerRepository servers;
    @Autowired ChannelRepository channels;
    @Autowired MessageRepository messages;
    @Autowired ReactionRepository reactions;

    private User alice;
    private User bob;
    private Message msg;

    @BeforeEach
    void setUp() {
        alice = users.save(new User("alice", "a@x", "x"));
        bob   = users.save(new User("bob",   "b@x", "x"));
        Server server = servers.save(new Server("s1"));
        Channel chan = channels.save(new Channel(server, "general"));
        msg = messages.save(new Message(chan, alice, "hi"));
    }

    @Test
    void addsAndCountsReactions() {
        reactions.save(new Reaction(msg, alice, "👍"));
        reactions.save(new Reaction(msg, bob,   "👍"));
        reactions.save(new Reaction(msg, bob,   "🎉"));

        List<Object[]> rows = reactions.countByEmoji(msg.getId());
        assertThat(rows).hasSize(2);

        // Each row is [emoji, count]
        long thumbs = rows.stream()
                .filter(r -> r[0].equals("👍"))
                .mapToLong(r -> (Long) r[1])
                .findFirst().orElseThrow();
        long party = rows.stream()
                .filter(r -> r[0].equals("🎉"))
                .mapToLong(r -> (Long) r[1])
                .findFirst().orElseThrow();
        assertThat(thumbs).isEqualTo(2L);
        assertThat(party).isEqualTo(1L);
    }

    @Test
    void uniquePerUserPerEmoji() {
        reactions.save(new Reaction(msg, alice, "👍"));
        // Same user, same emoji — the second insert must violate the unique constraint
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            reactions.saveAndFlush(new Reaction(msg, alice, "👍"));
        }).isInstanceOf(Exception.class);
    }

    @Test
    void deleteByMessageIdCascades() {
        reactions.save(new Reaction(msg, alice, "👍"));
        reactions.save(new Reaction(msg, bob,   "🎉"));
        assertThat(reactions.findByMessageId(msg.getId())).hasSize(2);

        reactions.deleteByMessageId(msg.getId());
        messages.deleteById(msg.getId());

        assertThat(reactions.findByMessageId(msg.getId())).isEmpty();
    }
}
