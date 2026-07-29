package com.felipeb.discordclone.auth.api;

import com.felipeb.discordclone.auth.JwtService;
import com.felipeb.discordclone.auth.UserService;
import com.felipeb.discordclone.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService users;
    private final JwtService jwt;

    public AuthController(UserService users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        UserService.Authenticated auth = users.register(req.username(), req.email(), req.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(auth.user(), auth.token()));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        UserService.Authenticated auth = users.login(req.username(), req.password());
        return AuthResponse.from(auth.user(), auth.token());
    }

    @GetMapping("/me")
    public AuthResponse me(@RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        // Re-issue a token so the client can refresh without logging in again
        return AuthResponse.from(user, jwt.issue(user.getId(), user.getUsername()));
    }

    private Long extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Bearer token");
        }
        try {
            return jwt.parse(authHeader.substring("Bearer ".length())).userId();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }
}
