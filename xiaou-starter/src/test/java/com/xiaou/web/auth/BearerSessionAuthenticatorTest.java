package com.xiaou.web.auth;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BearerSessionAuthenticatorTest {

    @Test
    void validBearerReturnsStoredUsername() {
        AuthSessionRepository sessions = mock(AuthSessionRepository.class);
        when(sessions.findUsername("opaque-token")).thenReturn(Optional.of("demo-admin-a"));
        BearerSessionAuthenticator authenticator = new BearerSessionAuthenticator(sessions);

        assertThat(authenticator.requireUsername("Bearer opaque-token")).isEqualTo("demo-admin-a");
    }

    @Test
    void bearerSchemeIsCaseInsensitive() {
        AuthSessionRepository sessions = mock(AuthSessionRepository.class);
        when(sessions.findUsername("opaque-token")).thenReturn(Optional.of("demo-admin-a"));
        BearerSessionAuthenticator authenticator = new BearerSessionAuthenticator(sessions);

        assertThat(authenticator.requireUsername("bearer opaque-token")).isEqualTo("demo-admin-a");
    }

    @Test
    void missingMalformedBlankOrExpiredTokenIsUnauthorized() {
        AuthSessionRepository sessions = mock(AuthSessionRepository.class);
        when(sessions.findUsername("expired-token")).thenReturn(Optional.empty());
        BearerSessionAuthenticator authenticator = new BearerSessionAuthenticator(sessions);

        assertThatThrownBy(() -> authenticator.requireUsername(null))
                .isInstanceOf(InvalidSessionException.class);
        assertThatThrownBy(() -> authenticator.requireUsername(""))
                .isInstanceOf(InvalidSessionException.class);
        assertThatThrownBy(() -> authenticator.requireUsername("Basic abc"))
                .isInstanceOf(InvalidSessionException.class);
        assertThatThrownBy(() -> authenticator.requireUsername("Bearer "))
                .isInstanceOf(InvalidSessionException.class);
        assertThatThrownBy(() -> authenticator.requireUsername("Bearer expired-token"))
                .isInstanceOf(InvalidSessionException.class);
    }
}
