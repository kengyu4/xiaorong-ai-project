package com.xiaorong.assistant.auth.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "xiaorong.persistence", name = "enabled", havingValue = "true")
public class AuthSchemaInitializer implements ApplicationRunner {
    private final AuthJdbcRepository repository;

    public AuthSchemaInitializer(AuthJdbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        repository.initSchema();
    }
}
