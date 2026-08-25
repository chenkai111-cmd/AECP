package com.xiaou.web.auth;

public record AuthLoginResult(String token, long expiresInSeconds, String username) {
}
