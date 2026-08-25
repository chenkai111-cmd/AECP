package com.xiaou.web.auth;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

public class AuthService {

    private static final int TOKEN_BYTES = 32;

    private final PasswordEncoder passwordEncoder;
    private final AuthSessionRepository sessionRepository;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String demoPasswordHash;

    public AuthService(
            PasswordEncoder passwordEncoder,
            AuthSessionRepository sessionRepository,
            AuthProperties properties) {
        this.passwordEncoder = passwordEncoder;
        this.sessionRepository = sessionRepository;
        this.properties = properties;
        this.demoPasswordHash = isBcryptHash(properties.getDemoPassword())
                ? properties.getDemoPassword()
                : passwordEncoder.encode(properties.getDemoPassword());
    }

    public AuthLoginResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new InvalidCredentialsException();
        }
        if (!properties.getDemoUsername().equals(username)
                || !passwordEncoder.matches(password, demoPasswordHash)) {
            throw new InvalidCredentialsException();
        }

        String token = generateToken();
        long ttlSeconds = properties.getSessionTtlSeconds();
        sessionRepository.save(token, username, Duration.ofSeconds(ttlSeconds));
        return new AuthLoginResult(token, ttlSeconds, username);
    }

    public AuthLogoutResult logout(String token) {
        if (token == null || token.isBlank()) {
            return new AuthLogoutResult(false);
        }
        return new AuthLogoutResult(sessionRepository.delete(token));
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean isBcryptHash(String value) {
        return value != null && value.startsWith("$2");
    }
}
