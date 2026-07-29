package com.felipeb.discordclone.chat.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Optional<Channel> findByName(String name);
    boolean existsByName(String name);
}
