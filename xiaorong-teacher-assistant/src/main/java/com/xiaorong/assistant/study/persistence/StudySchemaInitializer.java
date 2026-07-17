package com.xiaorong.assistant.study.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.config.XiaorongProperties;
import com.xiaorong.assistant.study.content.StudyMaterial;
import com.xiaorong.assistant.study.content.StudyTemplateProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@ConditionalOnProperty(prefix = "xiaorong.persistence", name = "enabled", havingValue = "true")
public class StudySchemaInitializer implements ApplicationRunner {
    private final StudyJdbcRepository repository;
    private final StudyTemplateProvider templateProvider;
    private final StudyMaterialCache cache;
    private final ObjectMapper objectMapper;
    private final XiaorongProperties properties;

    public StudySchemaInitializer(StudyJdbcRepository repository,
                                  StudyTemplateProvider templateProvider,
                                  StudyMaterialCache cache,
                                  ObjectMapper objectMapper,
                                  XiaorongProperties properties) {
        this.repository = repository;
        this.templateProvider = templateProvider;
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        repository.initSchema();
        if (!properties.getPersistence().isSeedOnStartup()) {
            return;
        }
        for (StudyMaterial material : templateProvider.loadMaterials()) {
            String json = toJson(material);
            String contentHash = sha256(json);
            repository.upsertMaterial(material, contentHash, properties.getPromptVersion());
            cache.put(material.course().courseId(), material);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("课程材料 JSON 序列化失败", ex);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
