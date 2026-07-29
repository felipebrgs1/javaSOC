package com.felipeb.discordclone.server;

import com.felipeb.discordclone.user.User;
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
    name = "memberships",
    uniqueConstraints = @UniqueConstraint(name = "uk_membership_user_server", columnNames = {"user_id", "server_id"})
)
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected Membership() {
        // JPA
    }

    public Membership(User user, Server server, Role role) {
        this.user = user;
        this.server = server;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Server getServer() { return server; }
    public Role getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Membership other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
