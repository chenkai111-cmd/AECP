package com.xiaou.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.xiaou.web", "com.xiaou.aecp.identity"})
@ConfigurationPropertiesScan(basePackages = "com.xiaou.web")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
