package com.xiaou.web.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthLoginResult(
        String token,
        @JsonProperty("expires_in_seconds") long expiresInSeconds,
        String username) {
}
