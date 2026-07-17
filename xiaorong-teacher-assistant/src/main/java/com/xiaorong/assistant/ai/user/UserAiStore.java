package com.xiaorong.assistant.ai.user;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserAiStore {
    Optional<UserProviderSecretRow> findSecret(long userId, String providerCode);

    List<UserProviderSecretRow> findSecrets(long userId);

    void upsertSecret(UserProviderSecretRow row);

    boolean deleteSecret(long userId, String providerCode);

    Optional<UserAiPreferenceRow> findPreference(long userId);

    void upsertPreference(UserAiPreferenceRow row);

    void deletePreference(long userId);

    void initSchema();

    record UserProviderSecretRow(
            long userId,
            String providerCode,
            String encryptedApiKey,
            String encryptionIv,
            String keyVersion,
            String apiKeyLastFour,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    record UserAiPreferenceRow(
            long userId,
            String providerCode,
            String model,
            LocalDateTime updatedAt
    ) {
    }
}
