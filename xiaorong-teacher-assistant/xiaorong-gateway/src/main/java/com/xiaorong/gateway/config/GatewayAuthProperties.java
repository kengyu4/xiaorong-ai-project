package com.xiaorong.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "xiaorong.gateway.auth")
public class GatewayAuthProperties {
    private boolean enabled = true;
    private String redisPrefix = "xiaorong:";
    private List<String> ignoreWhites = new ArrayList<>(List.of(
            "/api/auth/login",
            "/api/auth/register"
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRedisPrefix() {
        return redisPrefix;
    }

    public void setRedisPrefix(String redisPrefix) {
        this.redisPrefix = redisPrefix;
    }

    public List<String> getIgnoreWhites() {
        return ignoreWhites;
    }

    public void setIgnoreWhites(List<String> ignoreWhites) {
        this.ignoreWhites = ignoreWhites == null ? new ArrayList<>() : ignoreWhites;
    }
}
