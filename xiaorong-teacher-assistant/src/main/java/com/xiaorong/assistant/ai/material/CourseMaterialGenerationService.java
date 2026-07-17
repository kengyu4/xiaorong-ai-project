package com.xiaorong.assistant.ai.material;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.config.XiaorongProperties;
import com.xiaorong.assistant.study.content.StudyMaterial;
import com.xiaorong.assistant.study.content.StudyTemplateProvider;
import com.xiaorong.assistant.study.persistence.StudyJdbcRepository;
import com.xiaorong.assistant.study.persistence.StudyMaterialCache;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@ConditionalOnProperty(prefix = "xiaorong.persistence", name = "enabled", havingValue = "true")
public class CourseMaterialGenerationService {
    private final RabbitTemplate rabbitTemplate;
    private final StudyTemplateProvider templateProvider;
    private final StudyJdbcRepository repository;
    private final StudyMaterialCache cache;
    private final ObjectMapper objectMapper;
    private final XiaorongProperties properties;

    public CourseMaterialGenerationService(RabbitTemplate rabbitTemplate,
                                           StudyTemplateProvider templateProvider,
                                           StudyJdbcRepository repository,
                                           StudyMaterialCache cache,
                                           ObjectMapper objectMapper,
                                           XiaorongProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.templateProvider = templateProvider;
        this.repository = repository;
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public CourseMaterialGenerationJob publish(Long courseId, boolean force, String promptVersion) {
        String taskId = newTaskId();
        String normalizedPromptVersion = normalizePromptVersion(promptVersion);
        Long materialId = repository.createPendingMaterial(courseId, normalizedPromptVersion, taskId);
        CourseMaterialGenerationJob job = new CourseMaterialGenerationJob(
                taskId,
                materialId,
                courseId,
                force,
                normalizedPromptVersion
        );
        rabbitTemplate.convertAndSend(
                properties.getRabbitmq().getLessonMaterialExchange(),
                properties.getRabbitmq().getLessonMaterialRoutingKey(),
                toJson(job)
        );
        return job;
    }

    @RabbitListener(queues = "#{lessonMaterialQueue.name}")
    public void consume(String payload) {
        CourseMaterialGenerationJob job = readJob(payload);
        try {
            StudyMaterial material = templateProvider.loadMaterials().stream()
                    .filter(candidate -> candidate.course().courseId().equals(job.courseId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("课程不存在，无法生成材料: " + job.courseId()));
            String materialJson = toJson(material);
            repository.markMaterialGenerated(job.materialId(), material, sha256(materialJson), job.promptVersion());
            cache.put(material.course().courseId(), material);
        } catch (RuntimeException ex) {
            repository.markMaterialFailed(job.materialId(), ex.getMessage());
            throw ex;
        }
    }

    private CourseMaterialGenerationJob readJob(String payload) {
        try {
            return objectMapper.readValue(payload, CourseMaterialGenerationJob.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("课程材料生成任务解析失败", ex);
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

    private String newTaskId() {
        return "mq_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    private String normalizePromptVersion(String promptVersion) {
        return promptVersion == null || promptVersion.isBlank()
                ? properties.getPromptVersion()
                : promptVersion;
    }
}
