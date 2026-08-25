package com.xiaou.web.auth;

import java.time.Duration;
import java.util.Optional;

public interface AuthSessionRepository {

    void save(String token, String username, Duration ttl);

    boolean exists(String token);

    Optional<String> findUsername(String token);

    boolean delete(String token);
}
