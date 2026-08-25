package com.xiaou.web.redis;

import com.xiaou.redis.config.RedissonConfig;
import com.xiaou.redis.properties.RedissonProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import static org.assertj.core.api.Assertions.assertThat;

class RedissonConfigurationTest {

    @Test
    void registersRedissonPropertiesForAutoConfiguration() {
        EnableConfigurationProperties annotation = RedissonConfig.class
                .getAnnotation(EnableConfigurationProperties.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(RedissonProperties.class);
    }
}
