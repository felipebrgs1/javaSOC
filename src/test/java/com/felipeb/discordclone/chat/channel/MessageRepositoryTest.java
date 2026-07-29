package com.felipeb.discordclone.chat.channel;

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

    @Autowired ChannelRepository channels;
    @Autowired MessageRepository messages;

    private Channel general;
    private Channel dev;

    @BeforeEach
    void setUp() {
        general = channels.save(new Channel("general"));
        dev = channels.save(new Channel("dev"));
    }

    @Test
    void savesAndLoadsByChannelInOrder() {
        messages.save(new Message(general, "alice", "first"));
        messages.save(new Message(general, "bob", "second"));
        messages.save(new Message(dev, "alice", "off-topic"));

        List<Message> history = messages.findByChannelOrderByCreatedAtAsc(general, PageRequest.of(0, 50));

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getContent()).isEqualTo("first");
        assertThat(history.get(1).getContent()).isEqualTo("second");
        assertThat(history).extracting(Message::getAuthor).containsExactly("alice", "bob");
    }

    @Test
    void limitClampsResults() {
        for (int i = 0; i < 5; i++) {
            messages.save(new Message(general, "alice", "msg-" + i));
        }

        List<Message> top3 = messages.findByChannelOrderByCreatedAtAsc(general, PageRequest.of(0, 3));

        assertThat(top3).hasSize(3);
        assertThat(top3.get(0).getContent()).isEqualTo("msg-0");
        assertThat(top3.get(2).getContent()).isEqualTo("msg-2");
    }

    @Test
    void channelRepositoryFindsByName() {
        assertThat(channels.findByName("general")).contains(general);
        assertThat(channels.findByName("nope")).isEmpty();
    }
}
