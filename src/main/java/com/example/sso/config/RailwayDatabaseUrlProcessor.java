package com.example.sso.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts Railway's postgresql:// DATABASE_URL to jdbc:postgresql:// format
 * before Spring Boot's DataSource auto-configuration runs.
 *
 * Railway injects: DATABASE_URL=postgresql://user:pass@host:5432/db
 * Spring needs:    jdbc:postgresql://user:pass@host:5432/db
 *
 * This processor fires very early (before bean creation) so Hikari sees the
 * corrected URL. It is registered via spring.factories.
 */
public class RailwayDatabaseUrlProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {

        String rawUrl = environment.getProperty("DATABASE_URL");
        if (rawUrl == null || rawUrl.isBlank()) return;

        String jdbcUrl;
        if (rawUrl.startsWith("postgresql://")) {
            jdbcUrl = "jdbc:postgresql://" + rawUrl.substring("postgresql://".length());
        } else if (rawUrl.startsWith("postgres://")) {
            jdbcUrl = "jdbc:postgresql://" + rawUrl.substring("postgres://".length());
        } else {
            // Already jdbc: format or H2 — leave as-is
            return;
        }

        // Inject corrected values into a high-priority property source
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", jdbcUrl);
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        props.put("spring.jpa.properties.hibernate.dialect",
                  "org.hibernate.dialect.PostgreSQLDialect");
        // Railway embeds credentials in the URL — no separate username/password needed
        props.put("spring.datasource.username", "");
        props.put("spring.datasource.password", "");

        environment.getPropertySources()
            .addFirst(new MapPropertySource("railwayDatasource", props));
    }
}
