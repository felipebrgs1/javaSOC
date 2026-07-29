package com.felipeb.discordclone.chat.channel;

import com.felipeb.discordclone.server.Server;
import com.felipeb.discordclone.server.ServerRepository;
import com.felipeb.discordclone.user.User;
import com.felipeb.discordclone.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MessageRepositoryTest {

    @Autowired UserRepository users;
    @Autowired ServerRepository servers;
    @Autowired ChannelRepository channels;
    @Autowired MessageRepository messages;

    private User alice;
    private User bob;
    private Server general_server;
    private Server dev_server;
    private Channel general;
    private Channel dev;

    @BeforeEach
    void setUp() {
        alice = users.save(new User("alice", "alice@example.com", "x"));
        bob   = users.save(new User("bob",   "bob@example.com",   "x"));
        general_server = servers.save(new Server("general-server"));
        dev_server     = servers.save(new Server("dev-server"));
        general = channels.save(new Channel(general_server, "general"));
        dev     = channels.save(new Channel(dev_server, "dev"));
    }

    @Test
    void savesAndLoadsByChannelInOrder() {
        messages.save(new Message(general, alice, "first"));
        messages.save(new Message(general, bob,   "second"));
        messages.save(new Message(dev,     alice, "off-topic"));

        List<Message> history = messages.findByChannelOrderByCreatedAtAsc(general, PageRequest.of(0, 50));

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getContent()).isEqualTo("first");
        assertThat(history.get(1).getContent()).isEqualTo("second");
        assertThat(history).extracting(m -> m.getAuthor().getUsername()).containsExactly("alice", "bob");
    }

    @Test
    void limitClampsResults() {
        for (int i = 0; i < 5; i++) {
            messages.save(new Message(general, alice, "msg-" + i));
        }

        List<Message> top3 = messages.findByChannelOrderByCreatedAtAsc(general, PageRequest.of(0, 3));

        assertThat(top3).hasSize(3);
        assertThat(top3.get(0).getContent()).isEqualTo("msg-0");
        assertThat(top3.get(2).getContent()).isEqualTo("msg-2");
    }

    @Test
    void channelRepositoryFindsByServerAndName() {
        assertThat(channels.findByServerAndName(general_server, "general")).contains(general);
        assertThat(channels.findByServerAndName(general_server, "nope")).isEmpty();
        assertThat(channels.findByServer(general_server)).contains(general);
        assertThat(channels.findByServer(dev_server)).contains(dev);
    }
}
