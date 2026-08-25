package com.xiaou.web.auth;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class RedisAuthSessionRepository implements AuthSessionRepository {

    private final RedissonClient redissonClient;
    private final AuthProperties properties;

    public RedisAuthSessionRepository(RedissonClient redissonClient, AuthProperties properties) {
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    @Override
    public void save(String token, String username, Duration ttl) {
        bucket(token).set(username, ttl);
    }

    @Override
    public boolean exists(String token) {
        return bucket(token).isExists();
    }

    @Override
    public boolean delete(String token) {
        return bucket(token).delete();
    }

    private RBucket<String> bucket(String token) {
        return redissonClient.getBucket(properties.getSessionKeyPrefix() + token);
    }
}
