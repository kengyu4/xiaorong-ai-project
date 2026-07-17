package com.xiaorong.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "xiaorong")
public class XiaorongProperties {
    private Long mockUserId = 10001L;
    private String promptVersion = "lesson-v1";
    private final Study study = new Study();
    private final Persistence persistence = new Persistence();
    private final Cache cache = new Cache();
    private final Auth auth = new Auth();
    private final Rabbitmq rabbitmq = new Rabbitmq();
    private final Ai ai = new Ai();

    public Long getMockUserId() {
        return mockUserId;
    }

    public void setMockUserId(Long mockUserId) {
        this.mockUserId = mockUserId;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public Study getStudy() {
        return study;
    }

    public Persistence getPersistence() {
        return persistence;
    }

    public Cache getCache() {
        return cache;
    }

    public Auth getAuth() {
        return auth;
    }

    public Rabbitmq getRabbitmq() {
        return rabbitmq;
    }

    public Ai getAi() {
        return ai;
    }

    public static class Study {
        private String templatePath;

        public String getTemplatePath() {
            return templatePath;
        }

        public void setTemplatePath(String templatePath) {
            this.templatePath = templatePath;
        }
    }

    public static class Persistence {
        private boolean enabled;
        private boolean seedOnStartup = true;
        private String jdbcUrl;
        private String username;
        private String password;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isSeedOnStartup() {
            return seedOnStartup;
        }

        public void setSeedOnStartup(boolean seedOnStartup) {
            this.seedOnStartup = seedOnStartup;
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Cache {
        private boolean enabled;
        private String prefix = "xiaorong:";
        private long ttlSeconds = 3600;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    public static class Auth {
        private boolean enabled = true;
        private long tokenTtlSeconds = 86400;
        private int bcryptStrength = 12;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTokenTtlSeconds() {
            return tokenTtlSeconds;
        }

        public void setTokenTtlSeconds(long tokenTtlSeconds) {
            this.tokenTtlSeconds = tokenTtlSeconds;
        }

        public int getBcryptStrength() {
            return bcryptStrength;
        }

        public void setBcryptStrength(int bcryptStrength) {
            this.bcryptStrength = bcryptStrength;
        }
    }

    public static class Rabbitmq {
        private String lessonMaterialExchange = "xiaorong.ai.direct";
        private String lessonMaterialQueue = "xiaorong.ai.lesson-material.generate";
        private String lessonMaterialRoutingKey = "GENERATE_LESSON_MATERIAL";

        public String getLessonMaterialExchange() {
            return lessonMaterialExchange;
        }

        public void setLessonMaterialExchange(String lessonMaterialExchange) {
            this.lessonMaterialExchange = lessonMaterialExchange;
        }

        public String getLessonMaterialQueue() {
            return lessonMaterialQueue;
        }

        public void setLessonMaterialQueue(String lessonMaterialQueue) {
            this.lessonMaterialQueue = lessonMaterialQueue;
        }

        public String getLessonMaterialRoutingKey() {
            return lessonMaterialRoutingKey;
        }

        public void setLessonMaterialRoutingKey(String lessonMaterialRoutingKey) {
            this.lessonMaterialRoutingKey = lessonMaterialRoutingKey;
        }
    }

    public static class Ai {
        private boolean realEnabled;
        private String defaultProviderCode = "mock";
        private String secretMasterKey;
        private String secretMasterKeyFile;
        private List<Provider> providers = new ArrayList<>();

        public boolean isRealEnabled() {
            return realEnabled;
        }

        public void setRealEnabled(boolean realEnabled) {
            this.realEnabled = realEnabled;
        }

        public String getDefaultProviderCode() {
            return defaultProviderCode;
        }

        public void setDefaultProviderCode(String defaultProviderCode) {
            this.defaultProviderCode = defaultProviderCode;
        }

        public String getSecretMasterKey() {
            return secretMasterKey;
        }

        public void setSecretMasterKey(String secretMasterKey) {
            this.secretMasterKey = secretMasterKey;
        }

        public String getSecretMasterKeyFile() {
            return secretMasterKeyFile;
        }

        public void setSecretMasterKeyFile(String secretMasterKeyFile) {
            this.secretMasterKeyFile = secretMasterKeyFile;
        }

        public List<Provider> getProviders() {
            return providers;
        }

        public void setProviders(List<Provider> providers) {
            this.providers = providers == null ? new ArrayList<>() : providers;
        }
    }

    public static class Provider {
        private String providerCode;
        private String providerName;
        private String protocol;
        private String baseUrl;
        private String apiKey;
        private String defaultModel;
        private boolean supportStream = true;
        private boolean supportJson = true;
        private int priority = 100;
        private boolean enabled;

        public String getProviderCode() {
            return providerCode;
        }

        public void setProviderCode(String providerCode) {
            this.providerCode = providerCode;
        }

        public String getProviderName() {
            return providerName;
        }

        public void setProviderName(String providerName) {
            this.providerName = providerName;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public boolean isSupportStream() {
            return supportStream;
        }

        public void setSupportStream(boolean supportStream) {
            this.supportStream = supportStream;
        }

        public boolean isSupportJson() {
            return supportJson;
        }

        public void setSupportJson(boolean supportJson) {
            this.supportJson = supportJson;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
