package com.xiaorong.assistant.ai.user;

import com.xiaorong.assistant.ai.adapter.AiProviderAdapter;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatResponse;
import com.xiaorong.assistant.ai.secret.AiSecretCryptoService;
import com.xiaorong.assistant.ai.service.AiProviderRegistry;
import com.xiaorong.assistant.config.XiaorongProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAiSettingsServiceTest {
    private static final long USER_A = 10001L;
    private static final long USER_B = 10002L;

    @Test
    void storesEncryptedSecretAndOnlyReturnsMaskedMetadataForCurrentUser() {
        FakeUserAiStore store = new FakeUserAiStore();
        UserAiSettingsService service = service(store, true);

        service.saveSecret(USER_A, "deepseek", "sk-user-a");

        UserAiSettingsService.UserAiSettings settingsA = service.settings(USER_A);
        UserAiSettingsService.UserAiSettings settingsB = service.settings(USER_B);
        UserAiStore.UserProviderSecretRow stored = store.findSecret(USER_A, "deepseek").orElseThrow();

        assertThat(settingsA.providers())
                .extracting(UserAiSettingsService.UserProviderSetting::providerCode)
                .containsExactlyInAnyOrder("deepseek", "disabled");
        assertThat(settingsA.providers().stream()
                .filter(provider -> "deepseek".equals(provider.providerCode()))
                .findFirst().orElseThrow()).satisfies(provider -> {
                    assertThat(provider.configured()).isTrue();
                    assertThat(provider.maskedApiKey()).isEqualTo("****er-a");
                });
        assertThat(settingsA.toString()).doesNotContain("sk-user-a");
        assertThat(settingsB.providers().stream()
                .filter(provider -> "deepseek".equals(provider.providerCode()))
                .findFirst().orElseThrow()).satisfies(provider -> {
                    assertThat(provider.configured()).isFalse();
                    assertThat(provider.maskedApiKey()).isNull();
                });
        assertThat(stored.encryptedApiKey()).doesNotContain("sk-user-a");
        assertThat(stored.encryptionIv()).isNotBlank();
        assertThat(stored.keyVersion()).isEqualTo("v1");
    }

    @Test
    void replacingSecretProducesFreshCiphertextAndKeepsOnlyNewLastFour() {
        FakeUserAiStore store = new FakeUserAiStore();
        UserAiSettingsService service = service(store, true);
        service.saveSecret(USER_A, "deepseek", "sk-old-secret");
        UserAiStore.UserProviderSecretRow first = store.findSecret(USER_A, "deepseek").orElseThrow();

        service.saveSecret(USER_A, "deepseek", "sk-new-secret");
        UserAiStore.UserProviderSecretRow second = store.findSecret(USER_A, "deepseek").orElseThrow();

        assertThat(second.encryptedApiKey()).isNotEqualTo(first.encryptedApiKey());
        assertThat(second.encryptionIv()).isNotEqualTo(first.encryptionIv());
        assertThat(second.apiKeyLastFour()).isEqualTo("cret");
        assertThat(service.settings(USER_A).providers().stream()
                .filter(provider -> "deepseek".equals(provider.providerCode()))
                .findFirst().orElseThrow().maskedApiKey()).isEqualTo("****cret");
    }

    @Test
    void deletingSecretAlsoClearsPreferenceForThatProvider() {
        FakeUserAiStore store = new FakeUserAiStore();
        UserAiSettingsService service = service(store, true);
        service.saveSecret(USER_A, "deepseek", "sk-user-secret");
        service.savePreference(USER_A, "deepseek", "deepseek-chat");

        assertThat(service.deleteSecret(USER_A, "deepseek")).isTrue();

        assertThat(store.findSecret(USER_A, "deepseek")).isEmpty();
        assertThat(store.findPreference(USER_A)).isEmpty();
        assertThat(service.resolveRuntimeProvider(USER_A)).isEmpty();
    }

    @Test
    void allowsTrustedProviderWhenSystemProviderIsDisabledAndRejectsUnknownProvider() {
        FakeUserAiStore store = new FakeUserAiStore();
        UserAiSettingsService service = service(store, true);

        UserAiSettingsService.UserProviderSetting saved =
                service.saveSecret(USER_A, "disabled", "sk-user-secret");

        assertThat(saved.configured()).isTrue();
        assertThat(saved.maskedApiKey()).isEqualTo("****cret");
        assertThatThrownBy(() -> service.saveSecret(USER_A, "unknown", "sk-user-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("可信目录");
    }

    @Test
    void resolvesConfiguredSecretWithoutExplicitPreferenceUsingTrustedProviderOrderAndDefaultModel() {
        FakeUserAiStore store = new FakeUserAiStore();
        UserAiSettingsService service = service(store, true);
        service.saveSecret(USER_A, "disabled", "sk-disabled-secret");
        service.saveSecret(USER_A, "deepseek", "sk-deepseek-secret");

        UserAiSettingsService.RuntimeProviderSelection selection =
                service.resolveRuntimeProvider(USER_A).orElseThrow();
        UserAiSettingsService.UserAiSettings settings = service.settings(USER_A);

        assertThat(selection.provider().getProviderCode()).isEqualTo("deepseek");
        assertThat(selection.model()).isEqualTo("deepseek-chat");
        assertThat(selection.provider().getApiKey()).isEqualTo("sk-deepseek-secret");
        assertThat(settings.providerCode()).isEqualTo("deepseek");
        assertThat(settings.model()).isEqualTo("deepseek-chat");
        assertThat(settings.toString()).doesNotContain("sk-deepseek-secret", "sk-disabled-secret");
    }

    @Test
    void rejectsPreferenceWithoutSecret() {
        FakeUserAiStore store = new FakeUserAiStore();
        UserAiSettingsService service = service(store, true);

        assertThatThrownBy(() -> service.savePreference(USER_A, "deepseek", "deepseek-chat"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API Key");
    }

    @Test
    void providerTestUsesUsersSavedKeyAndPreferredModel() {
        FakeUserAiStore store = new FakeUserAiStore();
        RecordingAdapter adapter = new RecordingAdapter();
        UserAiSettingsService service = service(store, true, List.of(adapter));
        service.saveSecret(USER_A, "deepseek", "sk-user-secret");
        service.savePreference(USER_A, "deepseek", "my-selected-model");

        UserAiSettingsService.ProviderTestResult result = service.testProvider(USER_A, "deepseek");
        List<String> models = service.listModels(USER_A, "deepseek").models();

        assertThat(result.success()).isTrue();
        assertThat(result.model()).isEqualTo("my-selected-model");
        assertThat(adapter.seenApiKey).isEqualTo("sk-user-secret");
        assertThat(adapter.seenModel).isEqualTo("my-selected-model");
        assertThat(models).containsExactly("model-a", "model-b");
    }

    @Test
    void apiDtosSerializeWithoutFullApiKeyOrUserIdField() throws Exception {
        FakeUserAiStore store = new FakeUserAiStore();
        UserAiSettingsService service = service(store, true);
        service.saveSecret(USER_A, "deepseek", "sk-user-secret");
        UserAiDtos.SaveSecretRequest secretRequest = new UserAiDtos.SaveSecretRequest("sk-user-secret");
        UserAiDtos.SavePreferenceRequest preferenceRequest =
                new UserAiDtos.SavePreferenceRequest("deepseek", "deepseek-chat");

        String settingsJson = new ObjectMapper().writeValueAsString(service.settings(USER_A));
        String preferenceJson = new ObjectMapper().writeValueAsString(preferenceRequest);

        assertThat(secretRequest.apiKey()).isEqualTo("sk-user-secret");
        assertThat(settingsJson).doesNotContain("sk-user-secret");
        assertThat(settingsJson).doesNotContain("encryptedApiKey");
        assertThat(preferenceJson).doesNotContain("userId");
    }
    @Test
    void safelyFailsWritesWhenPersistenceOrMasterKeyIsUnavailable() {
        UserAiSettingsService noStore = service(null, true);
        UserAiSettingsService noCrypto = service(new FakeUserAiStore(), false);

        assertThat(noStore.settings(USER_A).persistenceAvailable()).isFalse();
        assertThatThrownBy(() -> noStore.saveSecret(USER_A, "deepseek", "sk-user-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("持久化");
        assertThat(noCrypto.settings(USER_A).secureStorageAvailable()).isFalse();
        assertThatThrownBy(() -> noCrypto.saveSecret(USER_A, "deepseek", "sk-user-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("安全存储");
    }

    private UserAiSettingsService service(UserAiStore store, boolean cryptoAvailable) {
        return service(store, cryptoAvailable, List.of());
    }

    private UserAiSettingsService service(
            UserAiStore store, boolean cryptoAvailable, List<AiProviderAdapter> adapters
    ) {
        XiaorongProperties properties = new XiaorongProperties();
        properties.getAi().setSecretMasterKey(cryptoAvailable ? masterKey() : "");
        properties.getAi().setProviders(List.of(
                provider("deepseek", "DeepSeek", true),
                provider("disabled", "Disabled", false)
        ));
        return new UserAiSettingsService(
                store,
                new AiSecretCryptoService(properties),
                new AiProviderRegistry(properties),
                adapters
        );
    }

    private XiaorongProperties.Provider provider(String code, String name, boolean enabled) {
        XiaorongProperties.Provider provider = new XiaorongProperties.Provider();
        provider.setProviderCode(code);
        provider.setProviderName(name);
        provider.setProtocol("openai-compatible");
        provider.setBaseUrl("https://example.com/v1");
        provider.setDefaultModel(code + "-chat");
        provider.setEnabled(enabled);
        return provider;
    }

    private String masterKey() {
        return Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    }


    private static final class RecordingAdapter implements AiProviderAdapter {
        private String seenApiKey;
        private String seenModel;

        @Override
        public String providerCode() {
            return "openai-compatible";
        }

        @Override
        public boolean supports(String protocol, String scene) {
            return "openai-compatible".equals(protocol);
        }

        @Override
        public AiChatResponse chat(AiChatRequest request, XiaorongProperties.Provider provider) {
            seenApiKey = provider.getApiKey();
            seenModel = request.model();
            return new AiChatResponse(provider.getProviderCode(), request.model(), request.scene(),
                    "ok", 0, 0, 1L, false);
        }

        @Override
        public List<String> listModels(XiaorongProperties.Provider provider) {
            seenApiKey = provider.getApiKey();
            return List.of("model-a", "model-b");
        }
    }

    private static final class FakeUserAiStore implements UserAiStore {
        private final Map<String, UserProviderSecretRow> secrets = new HashMap<>();
        private final Map<Long, UserAiPreferenceRow> preferences = new HashMap<>();

        @Override
        public Optional<UserProviderSecretRow> findSecret(long userId, String providerCode) {
            return Optional.ofNullable(secrets.get(key(userId, providerCode)));
        }

        @Override
        public List<UserProviderSecretRow> findSecrets(long userId) {
            return secrets.values().stream().filter(row -> row.userId() == userId).toList();
        }

        @Override
        public void upsertSecret(UserProviderSecretRow row) {
            secrets.put(key(row.userId(), row.providerCode()), row);
        }

        @Override
        public boolean deleteSecret(long userId, String providerCode) {
            return secrets.remove(key(userId, providerCode)) != null;
        }

        @Override
        public Optional<UserAiPreferenceRow> findPreference(long userId) {
            return Optional.ofNullable(preferences.get(userId));
        }

        @Override
        public void upsertPreference(UserAiPreferenceRow row) {
            preferences.put(row.userId(), row);
        }

        @Override
        public void deletePreference(long userId) {
            preferences.remove(userId);
        }

        @Override
        public void initSchema() {
        }

        private String key(long userId, String providerCode) {
            return userId + ":" + providerCode;
        }
    }
}
