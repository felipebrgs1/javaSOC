package com.felipeb.discordclone.chat.channel;

import com.felipeb.discordclone.server.Server;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
    name = "channels",
    uniqueConstraints = @UniqueConstraint(name = "uk_channel_server_name", columnNames = {"server_id", "name"})
)
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_permission", nullable = false, length = 16)
    private ChannelPermission defaultPermission = ChannelPermission.READ_WRITE;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ChannelType type = ChannelType.TEXT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Channel() {
        // JPA
    }

    public Channel(Server server, String name) {
        this.server = server;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public Channel(Server server, String name, ChannelPermission defaultPermission) {
        this(server, name);
        this.defaultPermission = defaultPermission;
    }

    public Channel(Server server, String name, ChannelPermission defaultPermission, ChannelType type) {
        this(server, name, defaultPermission);
        this.type = type;
    }

    public Long getId() { return id; }
    public Server getServer() { return server; }
    public String getName() { return name; }
    public ChannelPermission getDefaultPermission() { return defaultPermission; }
    public ChannelType getType() { return type; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDefaultPermission(ChannelPermission defaultPermission) {
        this.defaultPermission = defaultPermission;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Channel other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
