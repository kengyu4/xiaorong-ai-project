package com.xiaorong.assistant.ai.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class UserAiDtos {
    private UserAiDtos() {
    }

    public record SaveSecretRequest(
            @NotBlank @Size(min = 8, max = 4096) String apiKey
    ) {
    }

    public record SavePreferenceRequest(
            @NotBlank String providerCode,
            @NotBlank @Size(max = 200) String model
    ) {
    }

    public record UserAiSettingsResponse(
            boolean persistenceAvailable,
            boolean secureStorageAvailable,
            String providerCode,
            String model,
            List<UserProviderSettingResponse> providers
    ) {
    }

    public record UserProviderSettingResponse(
            String providerCode,
            String providerName,
            String protocol,
            String defaultModel,
            boolean enabled,
            boolean configured,
            String maskedApiKey
    ) {
    }

    public record ProviderTestResponse(
            boolean success,
            long latencyMs,
            String providerCode,
            String model,
            String message
    ) {
    }

    public record ProviderModelsResponse(String providerCode, List<String> models) {
    }

    public record DeleteSecretResponse(boolean deleted) {
    }

    public record PreferenceResponse(String providerCode, String model) {
    }
}