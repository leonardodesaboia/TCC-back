package com.allset.api.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartupLogger implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);

    private final Environment env;
    private final AppProperties appProperties;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String baseUrl = "http://localhost:" + appProperties.port();

        log.info("===========================================");
        log.info("  Health : {}/actuator/health", baseUrl);

        boolean swaggerEnabled = Boolean.parseBoolean(
                env.getProperty("springdoc.swagger-ui.enabled", "false"));

        if (swaggerEnabled) {
            log.info("  Docs   : {}/swagger-ui.html", baseUrl);
        }

        log.info("===========================================");
    }
}
