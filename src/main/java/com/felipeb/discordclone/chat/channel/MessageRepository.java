package com.felipeb.discordclone.chat.channel;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByChannelOrderByCreatedAtAsc(Channel channel, Pageable pageable);

    /**
     * Eagerly loads the message with its channel, server, and author in one
     * query, so the handler can use {@code message.getChannel().getServer()} and
     * {@code message.getAuthor()} after the @Transactional call ends without
     * hitting a lazy-init exception.
     */
    @Query("SELECT m FROM Message m " +
           "JOIN FETCH m.channel c " +
           "JOIN FETCH c.server " +
           "JOIN FETCH m.author " +
           "WHERE m.id = :id")
    Optional<Message> findByIdWithChannelAndServer(@Param("id") Long id);
}
