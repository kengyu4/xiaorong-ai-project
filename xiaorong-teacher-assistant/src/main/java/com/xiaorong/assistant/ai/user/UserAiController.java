package com.xiaorong.assistant.ai.user;

import com.xiaorong.assistant.auth.AuthContext;
import com.xiaorong.assistant.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.xiaorong.assistant.ai.user.UserAiDtos.*;

@RestController
@RequestMapping("/api/user/ai")
public class UserAiController {
    private final UserAiSettingsService settingsService;

    public UserAiController(UserAiSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/settings")
    public Result<UserAiSettingsResponse> settings() {
        long userId = AuthContext.requireUserId();
        return Result.success(toResponse(settingsService.settings(userId)));
    }

    @PutMapping("/providers/{providerCode}/secret")
    public Result<UserProviderSettingResponse> saveSecret(
            @PathVariable String providerCode,
            @Valid @RequestBody SaveSecretRequest request
    ) {
        long userId = AuthContext.requireUserId();
        return Result.success(toResponse(settingsService.saveSecret(userId, providerCode, request.apiKey())));
    }

    @DeleteMapping("/providers/{providerCode}/secret")
    public Result<DeleteSecretResponse> deleteSecret(@PathVariable String providerCode) {
        long userId = AuthContext.requireUserId();
        return Result.success(new DeleteSecretResponse(settingsService.deleteSecret(userId, providerCode)));
    }

    @PostMapping("/providers/{providerCode}/test")
    public Result<ProviderTestResponse> testProvider(@PathVariable String providerCode) {
        long userId = AuthContext.requireUserId();
        UserAiSettingsService.ProviderTestResult result = settingsService.testProvider(userId, providerCode);
        return Result.success(new ProviderTestResponse(
                result.success(), result.latencyMs(), result.providerCode(), result.model(), result.message()));
    }

    @GetMapping("/providers/{providerCode}/models")
    public Result<ProviderModelsResponse> listModels(@PathVariable String providerCode) {
        long userId = AuthContext.requireUserId();
        UserAiSettingsService.ProviderModelsResult result = settingsService.listModels(userId, providerCode);
        return Result.success(new ProviderModelsResponse(result.providerCode(), result.models()));
    }

    @PutMapping("/preference")
    public Result<PreferenceResponse> savePreference(@Valid @RequestBody SavePreferenceRequest request) {
        long userId = AuthContext.requireUserId();
        UserAiSettingsService.UserAiPreference result = settingsService.savePreference(
                userId, request.providerCode(), request.model());
        return Result.success(new PreferenceResponse(result.providerCode(), result.model()));
    }

    private UserAiSettingsResponse toResponse(UserAiSettingsService.UserAiSettings settings) {
        return new UserAiSettingsResponse(
                settings.persistenceAvailable(),
                settings.secureStorageAvailable(),
                settings.providerCode(),
                settings.model(),
                settings.providers().stream().map(this::toResponse).toList()
        );
    }

    private UserProviderSettingResponse toResponse(UserAiSettingsService.UserProviderSetting provider) {
        return new UserProviderSettingResponse(
                provider.providerCode(),
                provider.providerName(),
                provider.protocol(),
                provider.defaultModel(),
                provider.enabled(),
                provider.configured(),
                provider.maskedApiKey()
        );
    }
}