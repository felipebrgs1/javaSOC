package com.felipeb.discordclone.auth.api;

import com.felipeb.discordclone.user.User;

public record AuthResponse(
        Long id,
        String username,
        String email,
        String token
) {
    public static AuthResponse from(User user, String token) {
        return new AuthResponse(user.getId(), user.getUsername(), user.getEmail(), token);
    }
}
