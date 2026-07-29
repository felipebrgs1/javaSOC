package com.felipeb.discordclone.auth.api;

import com.felipeb.discordclone.auth.BadCredentialsException;
import com.felipeb.discordclone.auth.EmailTakenException;
import com.felipeb.discordclone.auth.UsernameTakenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(UsernameTakenException.class)
    public ProblemDetail handleUsernameTaken(UsernameTakenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(EmailTakenException.class)
    public ProblemDetail handleEmailTaken(EmailTakenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }
}
