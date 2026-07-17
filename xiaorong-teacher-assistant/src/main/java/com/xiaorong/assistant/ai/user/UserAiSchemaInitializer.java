package com.xiaorong.assistant.ai.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "xiaorong.persistence", name = "enabled", havingValue = "true")
public class UserAiSchemaInitializer implements ApplicationRunner {
    private final UserAiStore store;

    public UserAiSchemaInitializer(UserAiStore store) {
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) {
        store.initSchema();
    }
}
