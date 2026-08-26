package com.xiaou.web.organization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseTimeZoneConfigurationTest {

    @Test
    void developmentDatasourceUsesUtcConnectionAndSession() {
        Properties properties = load("application-dev.yml");

        assertThat(properties.getProperty("spring.datasource.url"))
                .contains("connectionTimeZone=UTC")
                .contains("forceConnectionTimeZoneToSession=true")
                .doesNotContain("serverTimezone=Asia/Shanghai");
        assertThat(properties.getProperty("spring.datasource.hikari.connection-init-sql"))
                .isEqualTo("SET time_zone = '+00:00'");
    }

    @Test
    void productionDatasourceInitializesUtcSession() {
        Properties properties = load("application-prod.yml");

        assertThat(properties.getProperty("spring.datasource.hikari.connection-init-sql"))
                .isEqualTo("SET time_zone = '+00:00'");
    }

    private Properties load(String resourceName) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(resourceName));
        return yaml.getObject();
    }
}
