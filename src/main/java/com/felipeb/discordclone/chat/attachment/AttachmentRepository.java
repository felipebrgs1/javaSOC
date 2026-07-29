package com.felipeb.discordclone.chat.attachment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByIdIn(Collection<Long> ids);

    Optional<Attachment> findByStoredFilename(String storedFilename);

    @Query("SELECT a FROM Attachment a WHERE a.message.id = :messageId")
    List<Attachment> findByMessageId(@Param("messageId") Long messageId);

    void deleteByMessageId(Long messageId);
}
