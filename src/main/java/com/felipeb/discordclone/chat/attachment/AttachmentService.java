package com.felipeb.discordclone.chat.attachment;

import com.felipeb.discordclone.user.User;
import com.felipeb.discordclone.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentRepository attachments;
    private final UserRepository users;
    private final Path uploadDir;
    private final long maxBytes;

    public AttachmentService(AttachmentRepository attachments,
                            UserRepository users,
                            @Value("${app.attachments.upload-dir:./uploads}") String uploadDir,
                            @Value("${app.attachments.max-bytes:10485760}") long maxBytes) {
        this.attachments = attachments;
        this.users = users;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create upload dir: " + this.uploadDir, e);
        }
    }

    @Transactional
    public Attachment store(MultipartFile file, Long uploaderUserId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("File exceeds max size of " + maxBytes + " bytes");
        }
        User uploader = users.findById(uploaderUserId)
                .orElseThrow(() -> new IllegalStateException("Uploader not found: " + uploaderUserId));

        String original = file.getOriginalFilename();
        String safeName = (original == null || original.isBlank()) ? "file" : original;
        String storedName = UUID.randomUUID() + "-" + safeName;
        Path target = uploadDir.resolve(storedName);

        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store upload", e);
        }

        Attachment a = new Attachment(
                safeName,
                file.getContentType(),
                file.getSize(),
                storedName,
                uploader
        );
        Attachment saved = attachments.save(a);
        log.info("Stored attachment id={} filename={} size={}", saved.getId(), safeName, file.getSize());
        return saved;
    }

    public Path resolveStoredFile(String storedFilename) {
        // Defends against ../ traversal
        Path candidate = uploadDir.resolve(storedFilename).normalize();
        if (!candidate.startsWith(uploadDir)) {
            throw new IllegalArgumentException("Invalid stored filename");
        }
        return candidate;
    }
}
