package com.xiaorong.assistant.ai.user;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "xiaorong.persistence", name = "enabled", havingValue = "true")
public class JdbcUserAiStore implements UserAiStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserAiStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserProviderSecretRow> findSecret(long userId, String providerCode) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select user_id, provider_code, encrypted_api_key, encryption_iv, key_version,
                           api_key_last_four, create_time, update_time
                    from ai_user_provider_secret
                    where user_id = ? and provider_code = ?
                    limit 1
                    """, (rs, rowNum) -> new UserProviderSecretRow(
                    rs.getLong("user_id"),
                    rs.getString("provider_code"),
                    rs.getString("encrypted_api_key"),
                    rs.getString("encryption_iv"),
                    rs.getString("key_version"),
                    rs.getString("api_key_last_four"),
                    toLocalDateTime(rs.getTimestamp("create_time")),
                    toLocalDateTime(rs.getTimestamp("update_time"))
            ), userId, providerCode));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<UserProviderSecretRow> findSecrets(long userId) {
        return jdbcTemplate.query("""
                select user_id, provider_code, encrypted_api_key, encryption_iv, key_version,
                       api_key_last_four, create_time, update_time
                from ai_user_provider_secret
                where user_id = ?
                order by provider_code
                """, (rs, rowNum) -> new UserProviderSecretRow(
                rs.getLong("user_id"),
                rs.getString("provider_code"),
                rs.getString("encrypted_api_key"),
                rs.getString("encryption_iv"),
                rs.getString("key_version"),
                rs.getString("api_key_last_four"),
                toLocalDateTime(rs.getTimestamp("create_time")),
                toLocalDateTime(rs.getTimestamp("update_time"))
        ), userId);
    }

    @Override
    public void upsertSecret(UserProviderSecretRow row) {
        jdbcTemplate.update("""
                insert into ai_user_provider_secret(
                    user_id, provider_code, encrypted_api_key, encryption_iv, key_version,
                    api_key_last_four, create_time, update_time
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update
                    encrypted_api_key = values(encrypted_api_key),
                    encryption_iv = values(encryption_iv),
                    key_version = values(key_version),
                    api_key_last_four = values(api_key_last_four),
                    update_time = values(update_time)
                """,
                row.userId(), row.providerCode(), row.encryptedApiKey(), row.encryptionIv(),
                row.keyVersion(), row.apiKeyLastFour(), row.createdAt(), row.updatedAt());
    }

    @Override
    public boolean deleteSecret(long userId, String providerCode) {
        return jdbcTemplate.update(
                "delete from ai_user_provider_secret where user_id = ? and provider_code = ?",
                userId, providerCode) > 0;
    }

    @Override
    public Optional<UserAiPreferenceRow> findPreference(long userId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select user_id, provider_code, model, update_time
                    from ai_user_ai_preference
                    where user_id = ?
                    limit 1
                    """, (rs, rowNum) -> new UserAiPreferenceRow(
                    rs.getLong("user_id"),
                    rs.getString("provider_code"),
                    rs.getString("model"),
                    toLocalDateTime(rs.getTimestamp("update_time"))
            ), userId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void upsertPreference(UserAiPreferenceRow row) {
        jdbcTemplate.update("""
                insert into ai_user_ai_preference(user_id, provider_code, model, update_time)
                values (?, ?, ?, ?)
                on duplicate key update
                    provider_code = values(provider_code),
                    model = values(model),
                    update_time = values(update_time)
                """, row.userId(), row.providerCode(), row.model(), row.updatedAt());
    }

    @Override
    public void deletePreference(long userId) {
        jdbcTemplate.update("delete from ai_user_ai_preference where user_id = ?", userId);
    }

    @Override
    public void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists ai_user_provider_secret (
                  user_id bigint not null,
                  provider_code varchar(64) not null,
                  encrypted_api_key text not null,
                  encryption_iv varchar(64) not null,
                  key_version varchar(20) not null,
                  api_key_last_four varchar(16) not null,
                  create_time datetime not null,
                  update_time datetime not null,
                  primary key (user_id, provider_code),
                  key idx_ai_user_provider_secret_provider (provider_code)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists ai_user_ai_preference (
                  user_id bigint not null primary key,
                  provider_code varchar(64) not null,
                  model varchar(200) not null,
                  update_time datetime not null,
                  key idx_ai_user_ai_preference_provider (provider_code)
                )
                """);
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
