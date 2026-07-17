package com.xiaorong.assistant.ai.user;

import com.xiaorong.assistant.ai.adapter.AiProviderAdapter;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatResponse;
import com.xiaorong.assistant.ai.dto.AiDtos.AiMessage;
import com.xiaorong.assistant.ai.secret.AiSecretCryptoService;
import com.xiaorong.assistant.ai.service.AiProviderRegistry;
import com.xiaorong.assistant.config.XiaorongProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserAiSettingsService {
    private static final int MIN_API_KEY_LENGTH = 8;
    private static final int MAX_API_KEY_LENGTH = 4096;
    private static final int MAX_MODEL_LENGTH = 200;

    private final UserAiStore store;
    private final AiSecretCryptoService cryptoService;
    private final AiProviderRegistry providerRegistry;
    private final List<AiProviderAdapter> adapters;

    @Autowired
    public UserAiSettingsService(
            ObjectProvider<UserAiStore> storeProvider,
            AiSecretCryptoService cryptoService,
            AiProviderRegistry providerRegistry,
            List<AiProviderAdapter> adapters
    ) {
        this(storeProvider.getIfAvailable(), cryptoService, providerRegistry, adapters);
    }

    UserAiSettingsService(
            UserAiStore store,
            AiSecretCryptoService cryptoService,
            AiProviderRegistry providerRegistry
    ) {
        this(store, cryptoService, providerRegistry, List.of());
    }

    UserAiSettingsService(
            UserAiStore store,
            AiSecretCryptoService cryptoService,
            AiProviderRegistry providerRegistry,
            List<AiProviderAdapter> adapters
    ) {
        this.store = store;
        this.cryptoService = cryptoService;
        this.providerRegistry = providerRegistry;
        this.adapters = List.copyOf(adapters);
    }

    public UserAiSettings settings(long userId) {
        Map<String, UserAiStore.UserProviderSecretRow> secrets = store == null
                ? Map.of()
                : store.findSecrets(userId).stream().collect(Collectors.toMap(
                        UserAiStore.UserProviderSecretRow::providerCode,
                        Function.identity(),
                        (left, right) -> right
                ));
        List<UserProviderSetting> providers = providerRegistry.list().stream()
                .map(provider -> toSetting(provider, secrets.get(provider.getProviderCode())))
                .toList();
        Optional<EffectiveProviderChoice> effectiveChoice = store == null
                ? Optional.empty()
                : resolveEffectiveChoice(userId, secrets);
        return new UserAiSettings(
                store != null,
                cryptoService.available(),
                effectiveChoice.map(choice -> choice.provider().getProviderCode()).orElse(null),
                effectiveChoice.map(EffectiveProviderChoice::model).orElse(null),
                providers
        );
    }

    public UserProviderSetting saveSecret(long userId, String providerCode, String apiKey) {
        UserAiStore requiredStore = requireStore();
        XiaorongProperties.Provider provider = requireTrustedProvider(providerCode);
        String normalizedApiKey = normalizeApiKey(apiKey);
        AiSecretCryptoService.EncryptedSecret encrypted = cryptoService.encrypt(normalizedApiKey);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt = requiredStore.findSecret(userId, providerCode)
                .map(UserAiStore.UserProviderSecretRow::createdAt)
                .orElse(now);
        UserAiStore.UserProviderSecretRow row = new UserAiStore.UserProviderSecretRow(
                userId,
                provider.getProviderCode(),
                encrypted.ciphertext(),
                encrypted.iv(),
                encrypted.keyVersion(),
                lastFour(normalizedApiKey),
                createdAt,
                now
        );
        requiredStore.upsertSecret(row);
        return toSetting(provider, row);
    }

    public boolean deleteSecret(long userId, String providerCode) {
        UserAiStore requiredStore = requireStore();
        requireTrustedProvider(providerCode);
        boolean deleted = requiredStore.deleteSecret(userId, providerCode);
        requiredStore.findPreference(userId)
                .filter(preference -> providerCode.equals(preference.providerCode()))
                .ifPresent(preference -> requiredStore.deletePreference(userId));
        return deleted;
    }

    public UserAiPreference savePreference(long userId, String providerCode, String model) {
        UserAiStore requiredStore = requireStore();
        requireTrustedProvider(providerCode);
        if (requiredStore.findSecret(userId, providerCode).isEmpty()) {
            throw new IllegalArgumentException("请先为该 Provider 保存 API Key");
        }
        String normalizedModel = normalizeModel(model);
        UserAiStore.UserAiPreferenceRow row = new UserAiStore.UserAiPreferenceRow(
                userId, providerCode, normalizedModel, LocalDateTime.now());
        requiredStore.upsertPreference(row);
        return new UserAiPreference(row.providerCode(), row.model());
    }

    public ProviderTestResult testProvider(long userId, String providerCode) {
        long startedAt = System.currentTimeMillis();
        try {
            RuntimeProviderSelection selection = runtimeProviderForUser(userId, providerCode);
            AiProviderAdapter adapter = requireAdapter(selection.provider(), "provider-test");
            AiChatResponse response = adapter.chat(new AiChatRequest(
                    "provider-test",
                    providerCode,
                    selection.model(),
                    List.of(new AiMessage("user", "请用一句话回复：Provider 测试成功。")),
                    0.2,
                    120,
                    false,
                    null
            ), selection.provider());
            return new ProviderTestResult(
                    true,
                    System.currentTimeMillis() - startedAt,
                    providerCode,
                    response.model(),
                    response.content()
            );
        } catch (RuntimeException ex) {
            return new ProviderTestResult(
                    false,
                    System.currentTimeMillis() - startedAt,
                    providerCode,
                    null,
                    "Provider 操作失败"
            );
        }
    }

    public ProviderModelsResult listModels(long userId, String providerCode) {
        RuntimeProviderSelection selection = runtimeProviderForUser(userId, providerCode);
        AiProviderAdapter adapter = requireAdapter(selection.provider(), "models");
        return new ProviderModelsResult(providerCode, adapter.listModels(selection.provider()));
    }

    public Optional<RuntimeProviderSelection> resolveRuntimeProvider(long userId) {
        if (store == null || !cryptoService.available()) {
            return Optional.empty();
        }
        Map<String, UserAiStore.UserProviderSecretRow> secrets = store.findSecrets(userId).stream()
                .collect(Collectors.toMap(
                        UserAiStore.UserProviderSecretRow::providerCode,
                        Function.identity(),
                        (left, right) -> right
                ));
        return resolveEffectiveChoice(userId, secrets).map(choice -> {
            UserAiStore.UserProviderSecretRow secret = choice.secret();
            String apiKey = cryptoService.decrypt(new AiSecretCryptoService.EncryptedSecret(
                    secret.encryptedApiKey(), secret.encryptionIv(), secret.keyVersion()));
            XiaorongProperties.Provider runtimeProvider = copyProvider(choice.provider());
            runtimeProvider.setApiKey(apiKey);
            runtimeProvider.setDefaultModel(choice.model());
            return new RuntimeProviderSelection(runtimeProvider, choice.model());
        });
    }

    private Optional<EffectiveProviderChoice> resolveEffectiveChoice(
            long userId,
            Map<String, UserAiStore.UserProviderSecretRow> secrets
    ) {
        Optional<UserAiStore.UserAiPreferenceRow> preference = store.findPreference(userId);
        if (preference.isPresent()) {
            UserAiStore.UserAiPreferenceRow selected = preference.get();
            UserAiStore.UserProviderSecretRow secret = secrets.get(selected.providerCode());
            if (secret != null) {
                Optional<XiaorongProperties.Provider> trusted = providerRegistry.list().stream()
                        .filter(provider -> selected.providerCode().equals(provider.getProviderCode()))
                        .findFirst();
                if (trusted.isPresent()) {
                    String model = selected.model() == null || selected.model().isBlank()
                            ? trusted.get().getDefaultModel()
                            : selected.model();
                    return Optional.of(new EffectiveProviderChoice(trusted.get(), model, secret));
                }
            }
        }
        return providerRegistry.list().stream()
                .filter(provider -> secrets.containsKey(provider.getProviderCode()))
                .findFirst()
                .map(provider -> new EffectiveProviderChoice(
                        provider,
                        provider.getDefaultModel(),
                        secrets.get(provider.getProviderCode())
                ));
    }

    private RuntimeProviderSelection runtimeProviderForUser(long userId, String providerCode) {
        UserAiStore requiredStore = requireStore();
        XiaorongProperties.Provider trustedProvider = requireTrustedProvider(providerCode);
        UserAiStore.UserProviderSecretRow secret = requiredStore.findSecret(userId, providerCode)
                .orElseThrow(() -> new IllegalArgumentException("请先为该 Provider 保存 API Key"));
        String apiKey = cryptoService.decrypt(new AiSecretCryptoService.EncryptedSecret(
                secret.encryptedApiKey(), secret.encryptionIv(), secret.keyVersion()));
        String selectedModel = requiredStore.findPreference(userId)
                .filter(preference -> providerCode.equals(preference.providerCode()))
                .map(UserAiStore.UserAiPreferenceRow::model)
                .orElse(trustedProvider.getDefaultModel());
        XiaorongProperties.Provider runtimeProvider = copyProvider(trustedProvider);
        runtimeProvider.setApiKey(apiKey);
        runtimeProvider.setDefaultModel(selectedModel);
        return new RuntimeProviderSelection(runtimeProvider, selectedModel);
    }

    private AiProviderAdapter requireAdapter(XiaorongProperties.Provider provider, String scene) {
        return adapters.stream()
                .filter(adapter -> adapter.supports(provider.getProtocol(), scene))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Provider 操作失败"));
    }

    private UserProviderSetting toSetting(
            XiaorongProperties.Provider provider,
            UserAiStore.UserProviderSecretRow secret
    ) {
        return new UserProviderSetting(
                provider.getProviderCode(),
                provider.getProviderName(),
                provider.getProtocol(),
                provider.getDefaultModel(),
                provider.isEnabled(),
                secret != null,
                secret == null ? null : "****" + secret.apiKeyLastFour()
        );
    }

    private UserAiStore requireStore() {
        if (store == null) {
            throw new IllegalStateException("用户 AI 配置持久化未启用");
        }
        return store;
    }

    private XiaorongProperties.Provider requireTrustedProvider(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new IllegalArgumentException("Provider 不在可信目录");
        }
        return providerRegistry.list().stream()
                .filter(provider -> providerCode.equals(provider.getProviderCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Provider 不在可信目录"));
    }

    private String normalizeApiKey(String apiKey) {
        if (!cryptoService.available()) {
            throw new IllegalStateException("AI Key 安全存储未启用，请配置 32 字节 Base64 主密钥");
        }
        if (apiKey == null) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        String normalized = apiKey.trim();
        if (normalized.length() < MIN_API_KEY_LENGTH || normalized.length() > MAX_API_KEY_LENGTH) {
            throw new IllegalArgumentException("API Key 长度必须在 8 到 4096 个字符之间");
        }
        return normalized;
    }

    private String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("模型无效");
        }
        String normalized = model.trim();
        if (normalized.length() > MAX_MODEL_LENGTH) {
            throw new IllegalArgumentException("模型无效");
        }
        return normalized;
    }

    private String lastFour(String apiKey) {
        return apiKey.substring(apiKey.length() - 4);
    }

    private XiaorongProperties.Provider copyProvider(XiaorongProperties.Provider source) {
        XiaorongProperties.Provider target = new XiaorongProperties.Provider();
        target.setProviderCode(source.getProviderCode());
        target.setProviderName(source.getProviderName());
        target.setProtocol(source.getProtocol());
        target.setBaseUrl(source.getBaseUrl());
        target.setApiKey(source.getApiKey());
        target.setDefaultModel(source.getDefaultModel());
        target.setSupportStream(source.isSupportStream());
        target.setSupportJson(source.isSupportJson());
        target.setPriority(source.getPriority());
        target.setEnabled(source.isEnabled());
        return target;
    }

    public record UserAiSettings(
            boolean persistenceAvailable,
            boolean secureStorageAvailable,
            String providerCode,
            String model,
            List<UserProviderSetting> providers
    ) {
    }

    public record UserProviderSetting(
            String providerCode,
            String providerName,
            String protocol,
            String defaultModel,
            boolean enabled,
            boolean configured,
            String maskedApiKey
    ) {
    }

    public record UserAiPreference(String providerCode, String model) {
    }

    private record EffectiveProviderChoice(
            XiaorongProperties.Provider provider,
            String model,
            UserAiStore.UserProviderSecretRow secret
    ) {
    }

    public record RuntimeProviderSelection(XiaorongProperties.Provider provider, String model) {
    }

    public record ProviderTestResult(
            boolean success, long latencyMs, String providerCode, String model, String message
    ) {
    }

    public record ProviderModelsResult(String providerCode, List<String> models) {
    }
}

