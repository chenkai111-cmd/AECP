package com.xiaou.web.auth;

import java.time.Duration;

public interface AuthSessionRepository {

    void save(String token, String username, Duration ttl);

    boolean exists(String token);

    boolean delete(String token);
}
