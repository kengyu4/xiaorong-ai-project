package com.xiaorong.assistant.study.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.config.XiaorongProperties;
import com.xiaorong.assistant.study.content.StudyMaterial;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class StudyMaterialCache {
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;
    private final XiaorongProperties properties;

    public StudyMaterialCache(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                              ObjectMapper objectMapper,
                              XiaorongProperties properties) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<StudyMaterial> get(Long courseId) {
        if (!properties.getCache().isEnabled()) {
            return Optional.empty();
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return Optional.empty();
        }
        try {
            String json = redisTemplate.opsForValue().get(key(courseId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, StudyMaterial.class));
        } catch (RuntimeException | JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    public void put(Long courseId, StudyMaterial material) {
        if (!properties.getCache().isEnabled()) {
            return;
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    key(courseId),
                    objectMapper.writeValueAsString(material),
                    Duration.ofSeconds(properties.getCache().getTtlSeconds())
            );
        } catch (RuntimeException | JsonProcessingException ignored) {
            // Redis is an acceleration layer only; DB remains the source of truth.
        }
    }

    private String key(Long courseId) {
        return properties.getCache().getPrefix() + "study:material:" + courseId;
    }
}
