package com.felipeb.discordclone.chat.channel;

import com.felipeb.discordclone.chat.api.HistoryMessage;
import com.felipeb.discordclone.chat.api.OutgoingMessage;
import com.felipeb.discordclone.chat.attachment.Attachment;
import com.felipeb.discordclone.chat.attachment.AttachmentRepository;
import com.felipeb.discordclone.chat.reaction.Reaction;
import com.felipeb.discordclone.chat.reaction.ReactionRepository;
import com.felipeb.discordclone.server.Server;
import com.felipeb.discordclone.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ChannelService {

    /** Message + its Channel + Server, all initialized inside the @Transactional call
     *  so the handler can use them after the session closes. */
    public record MessageContext(Message message, Channel channel, Server server) {}

    private final ChannelRepository channels;
    private final MessageRepository messages;
    private final ReactionRepository reactions;
    private final AttachmentRepository attachments;

    public ChannelService(ChannelRepository channels,
                          MessageRepository messages,
                          ReactionRepository reactions,
                          AttachmentRepository attachments) {
        this.channels = channels;
        this.messages = messages;
        this.reactions = reactions;
        this.attachments = attachments;
    }

    @Transactional(readOnly = true)
    public Channel requireByServerAndName(Server server, String name) {
        return channels.findByServerAndName(server, name)
                .orElseThrow(() -> new ChannelNotFoundException(server.getName() + ":" + name));
    }

    @Transactional(readOnly = true)
    public Optional<Channel> findById(Long id) {
        return channels.findById(id);
    }

    /**
     * Loads a message together with its channel and server via JOIN FETCH,
     * so the handler can use the returned object after the @Transactional
     * boundary closes without hitting LazyInitializationException.
     */
    @Transactional(readOnly = true)
    public MessageContext loadMessageContext(Long id) {
        Message m = messages.findByIdWithChannelAndServer(id)
                .orElseThrow(() -> new MessageNotFoundException(id));
        return new MessageContext(m, m.getChannel(), m.getChannel().getServer());
    }

    @Transactional
    public Message publish(Channel channel, User author, String content, List<Long> attachmentIds) {
        Message msg = messages.save(new Message(channel, author, content));
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            List<Attachment> atts = attachments.findByIdIn(attachmentIds);
            for (Attachment a : atts) {
                if (a.getUploader().getId().equals(author.getId()) && a.getMessage() == null) {
                    a.attachTo(msg);
                }
            }
            attachments.saveAll(atts);
        }
        return msg;
    }

    @Transactional
    public Message editContent(MessageContext ctx, String newContent) {
        ctx.message().editContent(newContent);
        return messages.save(ctx.message());
    }

    @Transactional
    public void delete(Long messageId) {
        reactions.deleteByMessageId(messageId);
        attachments.deleteByMessageId(messageId);
        messages.deleteById(messageId);
    }

    @Transactional
    public boolean toggleReaction(Long messageId, User user, String emoji, boolean add) {
        Optional<Reaction> existing = reactions.findByMessageIdAndUserIdAndEmoji(messageId, user.getId(), emoji);
        if (add && existing.isEmpty()) {
            Message m = messages.findById(messageId)
                    .orElseThrow(() -> new MessageNotFoundException(messageId));
            reactions.save(new Reaction(m, user, emoji));
            return true;
        }
        if (!add && existing.isPresent()) {
            reactions.delete(existing.get());
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<HistoryMessage.MessageView> recentViews(Channel channel, int limit, User perspectiveUser) {
        List<Message> recent = messages.findByChannelOrderByCreatedAtAsc(channel, PageRequest.of(0, limit));
        if (recent.isEmpty()) {
            return List.of();
        }

        // Bulk-load reactions: build map of messageId -> list of (emoji, count, includesMe)
        Map<Long, List<OutgoingMessage.ReactionView>> rxByMessage = new HashMap<>();
        for (Message m : recent) {
            List<Object[]> rows = reactions.countByEmoji(m.getId());
            List<OutgoingMessage.ReactionView> views = new ArrayList<>();
            for (Object[] row : rows) {
                String emoji = (String) row[0];
                long count = (Long) row[1];
                boolean includesMe = perspectiveUser != null
                        && reactions.findByMessageIdAndUserIdAndEmoji(m.getId(), perspectiveUser.getId(), emoji).isPresent();
                views.add(new OutgoingMessage.ReactionView(emoji, count, includesMe));
            }
            rxByMessage.put(m.getId(), views);
        }

        // Bulk-load attachments per message
        Map<Long, List<OutgoingMessage.AttachmentView>> attByMessage = new HashMap<>();
        for (Message m : recent) {
            List<Attachment> atts = attachments.findByMessageId(m.getId());
            List<OutgoingMessage.AttachmentView> views = atts.stream()
                    .map(a -> new OutgoingMessage.AttachmentView(
                            a.getId(), a.getFilename(),
                            a.getContentType() == null ? "" : a.getContentType(),
                            a.getSizeBytes(),
                            "/api/attachments/" + a.getId() + "/download"))
                    .toList();
            attByMessage.put(m.getId(), views);
        }

        List<HistoryMessage.MessageView> result = new ArrayList<>();
        for (Message m : recent) {
            result.add(new HistoryMessage.MessageView(
                    m.getId(),
                    m.getAuthor().getUsername(),
                    m.getContent(),
                    m.getCreatedAt(),
                    m.getEditedAt(),
                    rxByMessage.getOrDefault(m.getId(), List.of()),
                    attByMessage.getOrDefault(m.getId(), List.of())
            ));
        }
        return result;
    }
}
