package com.felipeb.discordclone.auth;

public class EmailTakenException extends RuntimeException {
    public EmailTakenException(String email) {
        super("Email already registered: " + email);
    }
}
