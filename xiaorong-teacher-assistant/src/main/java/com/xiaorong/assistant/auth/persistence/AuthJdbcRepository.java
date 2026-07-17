package com.xiaorong.assistant.auth.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.auth.model.AuthUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "xiaorong.persistence", name = "enabled", havingValue = "true")
public class AuthJdbcRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuthJdbcRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists ai_user (
                  id bigint primary key auto_increment,
                  username varchar(50) not null,
                  nickname varchar(50),
                  password_hash varchar(120) not null,
                  roles json not null,
                  status varchar(20) not null default 'active',
                  last_login_time datetime,
                  create_time datetime,
                  update_time datetime,
                  unique key uk_ai_user_username (username)
                )
                """);
    }

    public Optional<AuthUser> findByUsername(String username) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select id, username, nickname, password_hash, status, roles
                    from ai_user
                    where username = ?
                    limit 1
                    """, (rs, rowNum) -> new AuthUser(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("nickname"),
                    rs.getString("password_hash"),
                    rs.getString("status"),
                    readRoles(rs.getString("roles"))
            ), username));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public AuthUser insertUser(String username, String nickname, String passwordHash, List<String> roles) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into ai_user(username, nickname, password_hash, roles, status, create_time, update_time)
                    values (?, ?, ?, ?, 'active', now(), now())
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, nickname);
            ps.setString(3, passwordHash);
            ps.setString(4, toJson(roles));
            return ps;
        }, keyHolder);
        Long userId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return new AuthUser(userId, username, nickname, passwordHash, "active", roles);
    }

    public void updateLastLogin(Long userId) {
        jdbcTemplate.update("update ai_user set last_login_time = now(), update_time = now() where id = ?", userId);
    }

    private List<String> readRoles(String json) {
        if (json == null || json.isBlank()) {
            return List.of("student");
        }
        try {
            return objectMapper.readerForListOf(String.class).readValue(json);
        } catch (JsonProcessingException ex) {
            return List.of("student");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("用户角色 JSON 序列化失败", ex);
        }
    }
}
