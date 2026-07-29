package com.felipeb.discordclone.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 32) String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 6, max = 100) String password
) {
}
