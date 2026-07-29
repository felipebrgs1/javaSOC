package com.felipeb.discordclone.chat.reaction;

import com.felipeb.discordclone.chat.channel.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findByMessageIdAndUserIdAndEmoji(Long messageId, Long userId, String emoji);

    List<Reaction> findByMessageId(Long messageId);

    void deleteByMessageId(Long messageId);

    /**
     * Aggregated count per emoji for a message. Returns rows of [emoji, count].
     */
    @Query("SELECT r.emoji, COUNT(r) FROM Reaction r WHERE r.message.id = :messageId GROUP BY r.emoji")
    List<Object[]> countByEmoji(@Param("messageId") Long messageId);
}
