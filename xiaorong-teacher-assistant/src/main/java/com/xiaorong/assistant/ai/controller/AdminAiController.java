package com.xiaorong.assistant.ai.controller;

import com.xiaorong.assistant.ai.dto.AdminAiDtos.*;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatResponse;
import com.xiaorong.assistant.ai.dto.AiDtos.AiMessage;
import com.xiaorong.assistant.ai.material.CourseMaterialGenerationJob;
import com.xiaorong.assistant.ai.material.CourseMaterialGenerationService;
import com.xiaorong.assistant.ai.service.AiProviderRegistry;
import com.xiaorong.assistant.ai.service.AiGatewayService;
import com.xiaorong.assistant.common.Result;
import com.xiaorong.assistant.config.XiaorongProperties;
import com.xiaorong.assistant.study.persistence.StudyJdbcRepository;
import com.xiaorong.assistant.study.persistence.StudyJdbcRepository.MaterialStatusRow;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiController {
    private final AiGatewayService aiGatewayService;
    private final AiProviderRegistry providerRegistry;
    private final ObjectProvider<CourseMaterialGenerationService> generationServiceProvider;
    private final ObjectProvider<StudyJdbcRepository> repositoryProvider;

    public AdminAiController(AiGatewayService aiGatewayService,
                             AiProviderRegistry providerRegistry,
                             ObjectProvider<CourseMaterialGenerationService> generationServiceProvider,
                             ObjectProvider<StudyJdbcRepository> repositoryProvider) {
        this.aiGatewayService = aiGatewayService;
        this.providerRegistry = providerRegistry;
        this.generationServiceProvider = generationServiceProvider;
        this.repositoryProvider = repositoryProvider;
    }

    @PostMapping("/course/{courseId}/generate")
    public Result<GenerateCourseMaterialResponse> generateCourseMaterial(
            @PathVariable Long courseId,
            @RequestBody GenerateCourseMaterialRequest request
    ) {
        CourseMaterialGenerationService generationService = generationServiceProvider.getIfAvailable();
        if (generationService == null) {
            throw new IllegalStateException("课程材料生成需要启用 xiaorong.persistence.enabled=true");
        }
        CourseMaterialGenerationJob job = generationService.publish(
                courseId,
                request != null && Boolean.TRUE.equals(request.force()),
                request == null ? null : request.promptVersion()
        );
        return Result.success(new GenerateCourseMaterialResponse(
                job.taskId(),
                job.materialId(),
                job.courseId(),
                "pending",
                "已发布课程材料生成任务，等待 RabbitMQ 消费。"
        ));
    }

    @GetMapping("/material/{materialId}")
    public Result<MaterialStatusResponse> getMaterialStatus(@PathVariable Long materialId) {
        StudyJdbcRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            throw new IllegalStateException("课程材料状态查询需要启用 xiaorong.persistence.enabled=true");
        }
        MaterialStatusRow status = repository.getMaterialStatus(materialId);
        return Result.success(toMaterialStatusResponse(status));
    }

    private MaterialStatusResponse toMaterialStatusResponse(MaterialStatusRow status) {
        String message = status.errorMsg() == null || status.errorMsg().isBlank()
                ? "课程材料状态来自 MySQL ai_lesson_material。"
                : status.errorMsg();
        return new MaterialStatusResponse(
                status.materialId(),
                status.courseId(),
                status.status(),
                status.promptVersion(),
                status.contentHash(),
                message
        );
    }

    @PostMapping("/providers")
    public Result<ProviderConfigResponse> saveProvider(@Valid @RequestBody ProviderConfigRequest request) {
        XiaorongProperties.Provider provider = providerRegistry.save(request);
        return Result.success(new ProviderConfigResponse(
                provider.getProviderCode(),
                provider.getProviderName(),
                provider.getProtocol(),
                provider.getBaseUrl(),
                provider.getDefaultModel(),
                provider.isSupportStream(),
                provider.isSupportJson(),
                provider.getPriority(),
                provider.isEnabled()
        ));
    }

    @PostMapping("/providers/{providerCode}/test")
    public Result<ProviderTestResponse> testProvider(@PathVariable String providerCode) {
        long startedAt = System.currentTimeMillis();
        try {
            AiChatResponse response = aiGatewayService.chat(new AiChatRequest(
                    "provider-test",
                    providerCode,
                    null,
                    List.of(new AiMessage("user", "请用一句话回复：小绒老师助教 Provider 测试成功。")),
                    0.2,
                    120,
                    false,
                    null
            ));
            return Result.success(new ProviderTestResponse(
                    true,
                    System.currentTimeMillis() - startedAt,
                    response.providerCode(),
                    response.model(),
                    response.content()
            ));
        } catch (RuntimeException ex) {
            return Result.success(new ProviderTestResponse(
                    false,
                    System.currentTimeMillis() - startedAt,
                    providerCode,
                    null,
                    ex.getMessage()
            ));
        }
    }
}
