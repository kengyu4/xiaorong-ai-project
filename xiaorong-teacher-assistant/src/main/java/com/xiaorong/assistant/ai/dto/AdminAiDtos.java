package com.xiaorong.assistant.ai.dto;

import jakarta.validation.constraints.NotBlank;

public final class AdminAiDtos {
    private AdminAiDtos() {
    }

    public record GenerateCourseMaterialRequest(
            Boolean force,
            String promptVersion
    ) {
    }

    public record GenerateCourseMaterialResponse(
            String taskId,
            Long materialId,
            Long courseId,
            String status,
            String message
    ) {
    }

    public record MaterialStatusResponse(
            Long materialId,
            Long courseId,
            String status,
            String promptVersion,
            String contentHash,
            String message
    ) {
    }

    public record ProviderConfigRequest(
            @NotBlank String providerCode,
            @NotBlank String providerName,
            @NotBlank String protocol,
            String baseUrl,
            String apiKey,
            String defaultModel,
            Boolean supportStream,
            Boolean supportJson,
            Integer priority,
            Boolean enabled
    ) {
    }

    public record ProviderConfigResponse(
            String providerCode,
            String providerName,
            String protocol,
            String baseUrl,
            String defaultModel,
            Boolean supportStream,
            Boolean supportJson,
            Integer priority,
            Boolean enabled
    ) {
    }

    public record ProviderTestResponse(
            Boolean success,
            Long latencyMs,
            String providerCode,
            String model,
            String reply
    ) {
    }
}
