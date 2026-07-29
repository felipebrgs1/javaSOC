package com.felipeb.discordclone.chat.channel;

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
    public Channel requireByName(String name) {
        return channels.findByName(name)
                .orElseThrow(() -> new ChannelNotFoundException(name));
    }

    @Transactional
    public Message publish(String channelName, String author, String content) {
        Channel channel = requireByName(channelName);
        return messages.save(new Message(channel, author, content));
    }

    @Transactional(readOnly = true)
    public List<Message> recent(Channel channel, int limit) {
        return messages.findByChannelOrderByCreatedAtAsc(channel, PageRequest.of(0, limit));
    }
}
