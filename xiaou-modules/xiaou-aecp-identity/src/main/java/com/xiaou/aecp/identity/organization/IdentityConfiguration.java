package com.xiaou.aecp.identity.organization;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class IdentityConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock identityClock() {
        return Clock.systemUTC();
    }
}
