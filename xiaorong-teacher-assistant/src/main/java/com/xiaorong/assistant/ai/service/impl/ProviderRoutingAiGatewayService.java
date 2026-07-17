package com.xiaorong.assistant.ai.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.ai.adapter.AiProviderAdapter;
import com.xiaorong.assistant.ai.dto.AiDtos.*;
import com.xiaorong.assistant.ai.service.AiProviderRegistry;
import com.xiaorong.assistant.ai.service.AiGatewayService;
import com.xiaorong.assistant.ai.service.AiGatewayService.AiStreamListener;
import com.xiaorong.assistant.config.XiaorongProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Service
public class ProviderRoutingAiGatewayService implements AiGatewayService {
    private final List<AiProviderAdapter> adapters;
    private final XiaorongProperties properties;
    private final AiProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;

    public ProviderRoutingAiGatewayService(List<AiProviderAdapter> adapters,
                                           XiaorongProperties properties,
                                           AiProviderRegistry providerRegistry,
                                           ObjectMapper objectMapper) {
        this.adapters = adapters;
        this.properties = properties;
        this.providerRegistry = providerRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        XiaorongProperties.Provider provider = selectProvider(request);
        AiProviderAdapter adapter = selectAdapter(provider, request.scene());
        return adapter.chat(request, provider);
    }

    @Override
    public AiStructuredResponse structured(AiStructuredRequest request) {
        AiChatResponse response = chat(new AiChatRequest(
                request.scene(),
                null,
                null,
                request.messages(),
                0.2,
                1200,
                false,
                "json_object"
        ));
        return new AiStructuredResponse(request.schemaName(), parseJsonObject(response.content()), false);
    }

    @Override
    public SseEmitter stream(AiChatRequest request) {
        XiaorongProperties.Provider provider = selectProvider(request);
        AiProviderAdapter adapter = selectAdapter(provider, request.scene());
        return adapter.stream(request, provider);
    }

    @Override
    public void stream(AiChatRequest request, AiStreamListener listener) {
        XiaorongProperties.Provider provider = selectProvider(request);
        AiProviderAdapter adapter = selectAdapter(provider, request.scene());
        adapter.stream(request, provider, listener);
    }

    private XiaorongProperties.Provider selectProvider(AiChatRequest request) {
        String requestedCode = request.providerCode();
        String targetCode = requestedCode == null || requestedCode.isBlank()
                ? properties.getAi().getDefaultProviderCode()
                : requestedCode;
        return providerRegistry.enabledProviders().stream()
                .filter(XiaorongProperties.Provider::isEnabled)
                .filter(provider -> targetCode == null || targetCode.isBlank() || provider.getProviderCode().equals(targetCode))
                .findFirst()
                .orElseGet(() -> providerRegistry.enabledProviders().stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("没有启用的 AI Provider，请在 xiaorong.ai.providers 中配置")));
    }

    private AiProviderAdapter selectAdapter(XiaorongProperties.Provider provider, String scene) {
        return adapters.stream()
                .filter(adapter -> adapter.supports(provider.getProtocol(), scene))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("没有可用的 AI Adapter：" + provider.getProtocol()));
    }

    private Map<String, Object> parseJsonObject(String content) {
        if (content == null || content.isBlank()) {
            return Map.of("content", "");
        }
        try {
            return objectMapper.readValue(content, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of("content", content);
        }
    }
}
