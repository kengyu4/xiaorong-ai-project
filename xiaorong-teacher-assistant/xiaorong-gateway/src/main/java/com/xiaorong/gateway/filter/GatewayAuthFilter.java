package com.xiaorong.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.gateway.config.GatewayAuthProperties;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final GatewayAuthProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayAuthFilter(StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper,
                             GatewayAuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getPath();
        if (isWhitePath(path)) {
            return chain.filter(exchange);
        }

        String token = extractBearerToken(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token.isBlank()) {
            return unauthorized(exchange.getResponse(), "缺少登录令牌");
        }

        try {
            String json = redisTemplate.opsForValue().get(sessionKey(token));
            if (json == null || json.isBlank()) {
                return unauthorized(exchange.getResponse(), "登录已过期，请重新登录");
            }
            JsonNode session = objectMapper.readTree(json);
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header("X-User-Id", session.path("userId").asText(""))
                    .header("X-Username", session.path("username").asText(""))
                    .header("X-User-Roles", session.path("roles").toString())
                    .build();
            return chain.filter(exchange.mutate().request(request).build());
        } catch (Exception ex) {
            return unauthorized(exchange.getResponse(), "登录校验失败");
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isWhitePath(String path) {
        for (String pattern : properties.getIgnoreWhites()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private String extractBearerToken(String header) {
        if (header == null || header.isBlank()) {
            return "";
        }
        String value = header.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }

    private String sessionKey(String token) {
        return properties.getRedisPrefix() + "auth:token:" + token;
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"" + escape(message) + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
