package com.xiaou.web.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAuthSessionRepositoryTest {

    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RBucket<String> bucket = mock(RBucket.class);
    private final AuthProperties properties = new AuthProperties();
    private RedisAuthSessionRepository repository;

    @BeforeEach
    void setUp() {
        properties.setSessionKeyPrefix("test:auth:session:");
        repository = new RedisAuthSessionRepository(redissonClient, properties);
    }

    @Test
    void saveUsesPrefixedKeyAndConfiguredTtl() {
        when(redissonClient.<String>getBucket("test:auth:session:opaque-token")).thenReturn(bucket);

        repository.save("opaque-token", "demo-pilot-pm", Duration.ofSeconds(600));

        verify(bucket).set("demo-pilot-pm", Duration.ofSeconds(600));
    }

    @Test
    void existsReadsTheSessionBucket() {
        when(redissonClient.<String>getBucket("test:auth:session:opaque-token")).thenReturn(bucket);
        when(bucket.isExists()).thenReturn(true);

        assertThat(repository.exists("opaque-token")).isTrue();
    }

    @Test
    void findUsernameReturnsStoredSessionValue() {
        when(redissonClient.<String>getBucket("test:auth:session:opaque-token")).thenReturn(bucket);
        when(bucket.get()).thenReturn("demo-admin-a");

        assertThat(repository.findUsername("opaque-token"))
                .contains("demo-admin-a");
    }

    @Test
    void findUsernameReturnsEmptyForMissingSession() {
        when(redissonClient.<String>getBucket("test:auth:session:missing-token")).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);

        assertThat(repository.findUsername("missing-token")).isEmpty();
    }

    @Test
    void deleteReturnsRedisDeletionResult() {
        when(redissonClient.<String>getBucket("test:auth:session:opaque-token")).thenReturn(bucket);
        when(bucket.delete()).thenReturn(true);

        assertThat(repository.delete("opaque-token")).isTrue();
    }
}
