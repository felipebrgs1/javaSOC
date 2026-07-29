package com.felipeb.discordclone.chat.channel;

import com.felipeb.discordclone.chat.api.HistoryMessage;
import com.felipeb.discordclone.server.Server;
import com.felipeb.discordclone.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChannelService {

    private final ChannelRepository channels;
    private final MessageRepository messages;

    public ChannelService(ChannelRepository channels, MessageRepository messages) {
        this.channels = channels;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public Channel requireByServerAndName(Server server, String name) {
        return channels.findByServerAndName(server, name)
                .orElseThrow(() -> new ChannelNotFoundException(server.getName() + ":" + name));
    }

    @Transactional
    public Message publish(Channel channel, User author, String content) {
        return messages.save(new Message(channel, author, content));
    }

    /**
     * Loads recent messages and projects them into DTOs while the persistence
     * context is still open, so we don't hit LazyInitializationException on
     * {@code message.getAuthor()}.
     */
    @Transactional(readOnly = true)
    public List<HistoryMessage.MessageView> recentViews(Channel channel, int limit) {
        return messages.findByChannelOrderByCreatedAtAsc(channel, PageRequest.of(0, limit))
                .stream()
                .map(m -> new HistoryMessage.MessageView(
                        m.getId(), m.getAuthor().getUsername(), m.getContent(), m.getCreatedAt()))
                .toList();
    }
}
