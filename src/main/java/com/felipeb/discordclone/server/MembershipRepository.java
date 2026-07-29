package com.felipeb.discordclone.server;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    Optional<Membership> findByUserIdAndServerId(Long userId, Long serverId);

    List<Membership> findByUserId(Long userId);

    List<Membership> findByServerId(Long serverId);

    boolean existsByUserIdAndServerId(Long userId, Long serverId);
}
