package com.felipeb.discordclone.chat.channel;

import com.felipeb.discordclone.server.Server;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Optional<Channel> findByServerAndName(Server server, String name);
    List<Channel> findByServer(Server server);
    boolean existsByServerAndName(Server server, String name);
}
