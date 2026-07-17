package com.xiaorong.assistant.ai.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public final class AiDtos {
    private AiDtos() {
    }

    public record AiMessage(
            String role,
            String content
    ) {
    }

    public record AiChatRequest(
            @NotBlank String scene,
            String providerCode,
            String model,
            List<AiMessage> messages,
            Double temperature,
            Integer maxTokens,
            Boolean stream,
            String responseFormat
    ) {
    }

    public record AiChatResponse(
            String providerCode,
            String model,
            String scene,
            String content,
            Integer promptTokens,
            Integer completionTokens,
            Long latencyMs,
            Boolean mock
    ) {
    }

    public record AiStructuredRequest(
            @NotBlank String scene,
            @NotBlank String schemaName,
            List<AiMessage> messages
    ) {
    }

    public record AiStructuredResponse(
            String schemaName,
            Map<String, Object> data,
            Boolean mock
    ) {
    }
}
