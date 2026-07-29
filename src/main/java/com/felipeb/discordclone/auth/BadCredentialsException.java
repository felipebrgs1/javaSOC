package com.felipeb.discordclone.auth;

public class BadCredentialsException extends RuntimeException {
    public BadCredentialsException() {
        super("Invalid username or password");
    }
}
