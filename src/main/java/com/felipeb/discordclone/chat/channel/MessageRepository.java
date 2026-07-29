package com.felipeb.discordclone.chat.channel;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByChannelOrderByCreatedAtAsc(Channel channel, Pageable pageable);
}
