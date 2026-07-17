package com.xiaorong.assistant.admin;

import com.xiaorong.assistant.ai.service.AiGatewayService;
import com.xiaorong.assistant.ai.service.AiProviderRegistry;
import com.xiaorong.assistant.common.Result;
import com.xiaorong.assistant.config.XiaorongProperties;
import com.xiaorong.assistant.study.service.StudyService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/runtime")
public class RuntimeStatusController {
    private final XiaorongProperties properties;
    private final StudyService studyService;
    private final AiGatewayService aiGatewayService;
    private final AiProviderRegistry providerRegistry;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public RuntimeStatusController(XiaorongProperties properties,
                                   StudyService studyService,
                                   AiGatewayService aiGatewayService,
                                   AiProviderRegistry providerRegistry,
                                   ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.properties = properties;
        this.studyService = studyService;
        this.aiGatewayService = aiGatewayService;
        this.providerRegistry = providerRegistry;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @GetMapping("/status")
    public Result<RuntimeStatus> status() {
        return Result.success(new RuntimeStatus(
                properties.getPersistence().isEnabled(),
                studyService.getClass().getSimpleName(),
                properties.getCache().isEnabled(),
                redisReachable(),
                properties.getAi().isRealEnabled(),
                properties.getAi().getDefaultProviderCode(),
                aiGatewayService.getClass().getSimpleName(),
                providerRegistry.list().stream()
                        .map(provider -> new ProviderStatus(
                                provider.getProviderCode(),
                                provider.getProtocol(),
                                provider.getBaseUrl(),
                                provider.getDefaultModel(),
                                provider.isEnabled(),
                                provider.getApiKey() != null && !provider.getApiKey().isBlank()
                        ))
                        .toList()
        ));
    }

    public record RuntimeStatus(
            boolean persistenceEnabled,
            String studyService,
            boolean cacheEnabled,
            boolean redisReachable,
            boolean aiRealEnabled,
            String defaultProviderCode,
            String aiGatewayService,
            List<ProviderStatus> providers
    ) {
    }

    public record ProviderStatus(
            String providerCode,
            String protocol,
            String baseUrl,
            String defaultModel,
            boolean enabled,
            boolean apiKeyConfigured
    ) {
    }

    private boolean redisReachable() {
        if (!properties.getCache().isEnabled()) {
            return false;
        }
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            return redisTemplate != null
                    && redisTemplate.getConnectionFactory() != null
                    && "PONG".equalsIgnoreCase(redisTemplate.getConnectionFactory().getConnection().ping());
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
