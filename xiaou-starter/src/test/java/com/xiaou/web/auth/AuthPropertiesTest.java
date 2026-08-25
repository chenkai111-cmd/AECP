package com.xiaou.web.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsNonPositiveSessionTtl() {
        AuthProperties properties = new AuthProperties();
        properties.setDemoUsername("demo-pilot-pm");
        properties.setDemoPassword("demo-password");
        properties.setSessionTtlSeconds(0);
        properties.setSessionKeyPrefix("test:auth:");

        assertThat(validator.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("sessionTtlSeconds");
    }

    @Test
    void bindsDevelopmentDemoUsername() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues(
                        "aecp.auth.demo-username=demo-pilot-pm",
                        "aecp.auth.demo-password=demo-password")
                .run(context -> assertThat(context.getBean(AuthProperties.class).getDemoUsername())
                        .isEqualTo("demo-pilot-pm"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthProperties.class)
    static class PropertiesConfiguration {
    }
}
