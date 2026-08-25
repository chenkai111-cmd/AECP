package com.xiaou.web.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void loginWithConfiguredCredentialsCreatesOpaqueSession() {
        InMemorySessionRepository sessions = new InMemorySessionRepository();
        AuthService service = new AuthService(passwordEncoder, sessions, properties());

        AuthLoginResult result = service.login("demo-pilot-pm", "demo-password");

        assertThat(result.token()).isNotBlank();
        assertThat(result.expiresInSeconds()).isEqualTo(600);
        assertThat(result.username()).isEqualTo("demo-pilot-pm");
        assertThat(sessions.values).containsEntry(result.token(), "demo-pilot-pm");
        assertThat(sessions.ttls).containsEntry(result.token(), Duration.ofSeconds(600));
    }

    @Test
    void loginWithWrongCredentialsDoesNotCreateSession() {
        InMemorySessionRepository sessions = new InMemorySessionRepository();
        AuthService service = new AuthService(passwordEncoder, sessions, properties());

        assertThatThrownBy(() -> service.login("demo-pilot-pm", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(sessions.values).isEmpty();
    }

    @Test
    void logoutDeletesExistingSessionAndReportsInvalidated() {
        InMemorySessionRepository sessions = new InMemorySessionRepository();
        AuthService service = new AuthService(passwordEncoder, sessions, properties());
        String token = service.login("demo-pilot-pm", "demo-password").token();

        AuthLogoutResult result = service.logout(token);

        assertThat(result.invalidated()).isTrue();
        assertThat(sessions.values).doesNotContainKey(token);
    }

    @Test
    void logoutOfMissingSessionIsIdempotent() {
        AuthService service = new AuthService(passwordEncoder, new InMemorySessionRepository(), properties());

        AuthLogoutResult result = service.logout("missing-token");

        assertThat(result.invalidated()).isFalse();
    }

    private AuthProperties properties() {
        AuthProperties properties = new AuthProperties();
        properties.setDemoUsername("demo-pilot-pm");
        properties.setDemoPassword(passwordEncoder.encode("demo-password"));
        properties.setSessionTtlSeconds(600);
        properties.setSessionKeyPrefix("test:auth:");
        return properties;
    }

    private static class InMemorySessionRepository implements AuthSessionRepository {

        private final Map<String, String> values = new HashMap<>();
        private final Map<String, Duration> ttls = new HashMap<>();

        @Override
        public void save(String token, String username, Duration ttl) {
            values.put(token, username);
            ttls.put(token, ttl);
        }

        @Override
        public boolean exists(String token) {
            return values.containsKey(token);
        }

        @Override
        public boolean delete(String token) {
            ttls.remove(token);
            return values.remove(token) != null;
        }
    }
}
