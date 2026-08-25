package com.xiaou.web.auth;

import org.springframework.stereotype.Component;

@Component
public class BearerSessionAuthenticator {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthSessionRepository sessions;

    public BearerSessionAuthenticator(AuthSessionRepository sessions) {
        this.sessions = sessions;
    }

    public String requireUsername(String authorization) {
        if (authorization == null || !authorization.regionMatches(
                true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new InvalidSessionException();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new InvalidSessionException();
        }
        return sessions.findUsername(token)
                .filter(username -> !username.isBlank())
                .orElseThrow(InvalidSessionException::new);
    }
}
