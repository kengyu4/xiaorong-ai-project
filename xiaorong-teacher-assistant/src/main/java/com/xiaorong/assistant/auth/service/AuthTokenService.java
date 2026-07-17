package com.xiaorong.assistant.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.auth.exception.UnauthorizedException;
import com.xiaorong.assistant.auth.model.AuthSession;
import com.xiaorong.assistant.auth.model.AuthUser;
import com.xiaorong.assistant.config.XiaorongProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Service
public class AuthTokenService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;
    private final XiaorongProperties properties;

    public AuthTokenService(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                            ObjectMapper objectMapper,
                            XiaorongProperties properties) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String createToken(AuthUser user) {
        String token = newToken();
        AuthSession session = new AuthSession(user.userId(), user.username(), user.nickname(), user.roles());
        try {
            redis().opsForValue().set(key(token), objectMapper.writeValueAsString(session), ttl());
            return token;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("登录会话创建失败", ex);
        }
    }

    public Optional<AuthSession> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            String json = redis().opsForValue().get(key(token));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            AuthSession session = objectMapper.readValue(json, AuthSession.class);
            redis().expire(key(token), ttl());
            return Optional.of(session);
        } catch (RuntimeException | JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        redis().delete(key(token));
    }

    public String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return "";
        }
        String value = authorizationHeader.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "xr_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String key(String token) {
        return properties.getCache().getPrefix() + "auth:token:" + token;
    }

    private Duration ttl() {
        return Duration.ofSeconds(Math.max(60, properties.getAuth().getTokenTtlSeconds()));
    }

    private StringRedisTemplate redis() {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            throw new UnauthorizedException("Redis 未配置，无法创建登录会话");
        }
        return redisTemplate;
    }
}
