package com.xiaorong.assistant.ai.service;

import com.xiaorong.assistant.ai.dto.AdminAiDtos.ProviderConfigRequest;
import com.xiaorong.assistant.config.XiaorongProperties;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiProviderRegistry {
    private final Map<String, XiaorongProperties.Provider> providers = new ConcurrentHashMap<>();

    public AiProviderRegistry(XiaorongProperties properties) {
        for (XiaorongProperties.Provider provider : properties.getAi().getProviders()) {
            if (provider.getProviderCode() != null && !provider.getProviderCode().isBlank()) {
                providers.put(provider.getProviderCode(), copy(provider));
            }
        }
    }

    public List<XiaorongProperties.Provider> list() {
        return providers.values().stream()
                .sorted(Comparator.comparingInt(XiaorongProperties.Provider::getPriority))
                .map(this::copy)
                .toList();
    }

    public List<XiaorongProperties.Provider> enabledProviders() {
        return providers.values().stream()
                .filter(XiaorongProperties.Provider::isEnabled)
                .sorted(Comparator.comparingInt(XiaorongProperties.Provider::getPriority))
                .map(this::copy)
                .toList();
    }

    public XiaorongProperties.Provider save(ProviderConfigRequest request) {
        XiaorongProperties.Provider provider = new XiaorongProperties.Provider();
        provider.setProviderCode(request.providerCode());
        provider.setProviderName(request.providerName());
        provider.setProtocol(request.protocol());
        provider.setBaseUrl(request.baseUrl());
        provider.setApiKey(request.apiKey());
        provider.setDefaultModel(request.defaultModel());
        provider.setSupportStream(Boolean.TRUE.equals(request.supportStream()));
        provider.setSupportJson(Boolean.TRUE.equals(request.supportJson()));
        provider.setPriority(request.priority() == null ? 100 : request.priority());
        provider.setEnabled(Boolean.TRUE.equals(request.enabled()));
        providers.put(provider.getProviderCode(), copy(provider));
        return provider;
    }

    private XiaorongProperties.Provider copy(XiaorongProperties.Provider source) {
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
}
