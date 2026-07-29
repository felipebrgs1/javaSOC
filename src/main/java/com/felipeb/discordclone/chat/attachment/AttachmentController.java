package com.felipeb.discordclone.chat.attachment;

import com.felipeb.discordclone.auth.JwtService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService service;
    private final AttachmentRepository repo;
    private final JwtService jwt;

    public AttachmentController(AttachmentService service, AttachmentRepository repo, JwtService jwt) {
        this.service = service;
        this.repo = repo;
        this.jwt = jwt;
    }

    @PostMapping
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        Attachment a = service.store(file, userId);
        return ResponseEntity.status(201).body(Map.of(
                "id", a.getId(),
                "filename", a.getFilename(),
                "contentType", a.getContentType() == null ? "" : a.getContentType(),
                "sizeBytes", a.getSizeBytes(),
                "url", "/api/attachments/" + a.getId() + "/download"
        ));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Attachment a = repo.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Attachment not found"));
        Path path = service.resolveStoredFile(a.getStoredFilename());
        Resource resource;
        try {
            resource = new UrlResource(path.toUri());
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        String contentType = a.getContentType() != null ? a.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(a.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + a.getFilename() + "\"")
                .body(resource);
    }

    private Long extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing Bearer token");
        }
        try {
            return jwt.parse(authHeader.substring("Bearer ".length())).userId();
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }
}
