package com.xiaorong.assistant.ai.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.ai.adapter.AiProviderAdapter;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatResponse;
import com.xiaorong.assistant.ai.dto.AiDtos.AiStructuredRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiStructuredResponse;
import com.xiaorong.assistant.ai.service.AiGatewayService;
import com.xiaorong.assistant.ai.service.AiGatewayService.AiStreamListener;
import com.xiaorong.assistant.ai.user.UserAiSettingsService;
import com.xiaorong.assistant.auth.AuthContext;
import com.xiaorong.assistant.config.XiaorongProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Primary
public class DynamicAiGatewayService implements AiGatewayService {
    private final MockAiGatewayService mockGateway;
    private final ProviderRoutingAiGatewayService systemGateway;
    private final UserAiSettingsService userSettingsService;
    private final XiaorongProperties properties;
    private final List<AiProviderAdapter> adapters;
    private final ObjectMapper objectMapper;

    public DynamicAiGatewayService(
            MockAiGatewayService mockGateway,
            ProviderRoutingAiGatewayService systemGateway,
            UserAiSettingsService userSettingsService,
            XiaorongProperties properties,
            List<AiProviderAdapter> adapters,
            ObjectMapper objectMapper
    ) {
        this.mockGateway = mockGateway;
        this.systemGateway = systemGateway;
        this.userSettingsService = userSettingsService;
        this.properties = properties;
        this.adapters = List.copyOf(adapters);
        this.objectMapper = objectMapper;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        Optional<Long> userId = currentUserId();
        if (userId.isEmpty()) {
            return backgroundGateway().chat(request);
        }
        Optional<UserAiSettingsService.RuntimeProviderSelection> selection =
                userSettingsService.resolveRuntimeProvider(userId.get());
        if (selection.isEmpty()) {
            return mockGateway.chat(request);
        }
        AiChatRequest selectedRequest = selectRequest(request, selection.get());
        return requireAdapter(selection.get().provider(), selectedRequest.scene())
                .chat(selectedRequest, selection.get().provider());
    }

    @Override
    public AiStructuredResponse structured(AiStructuredRequest request) {
        Optional<Long> userId = currentUserId();
        if (userId.isEmpty()) {
            return backgroundGateway().structured(request);
        }
        Optional<UserAiSettingsService.RuntimeProviderSelection> selection =
                userSettingsService.resolveRuntimeProvider(userId.get());
        if (selection.isEmpty()) {
            return mockGateway.structured(request);
        }
        AiChatRequest selectedRequest = new AiChatRequest(
                request.scene(),
                selection.get().provider().getProviderCode(),
                selection.get().model(),
                request.messages(),
                0.2,
                1200,
                false,
                "json_object"
        );
        AiChatResponse response = requireAdapter(selection.get().provider(), request.scene())
                .chat(selectedRequest, selection.get().provider());
        return new AiStructuredResponse(request.schemaName(), parseJsonObject(response.content()), false);
    }

    @Override
    public SseEmitter stream(AiChatRequest request) {
        Optional<Long> userId = currentUserId();
        if (userId.isEmpty()) {
            return backgroundGateway().stream(request);
        }
        Optional<UserAiSettingsService.RuntimeProviderSelection> selection =
                userSettingsService.resolveRuntimeProvider(userId.get());
        if (selection.isEmpty()) {
            return mockGateway.stream(request);
        }
        AiChatRequest selectedRequest = selectRequest(request, selection.get());
        return requireAdapter(selection.get().provider(), selectedRequest.scene())
                .stream(selectedRequest, selection.get().provider());
    }

    @Override
    public void stream(AiChatRequest request, AiStreamListener listener) {
        Optional<Long> userId = currentUserId();
        if (userId.isEmpty()) {
            backgroundGateway().stream(request, listener);
            return;
        }
        Optional<UserAiSettingsService.RuntimeProviderSelection> selection =
                userSettingsService.resolveRuntimeProvider(userId.get());
        if (selection.isEmpty()) {
            mockGateway.stream(request, listener);
            return;
        }
        AiChatRequest selectedRequest = selectRequest(request, selection.get());
        requireAdapter(selection.get().provider(), selectedRequest.scene())
                .stream(selectedRequest, selection.get().provider(), listener);
    }

    private Optional<Long> currentUserId() {
        return AuthContext.current().map(session -> session.userId());
    }

    private AiGatewayService backgroundGateway() {
        return properties.getAi().isRealEnabled() ? systemGateway : mockGateway;
    }

    private AiChatRequest selectRequest(
            AiChatRequest request,
            UserAiSettingsService.RuntimeProviderSelection selection
    ) {
        return new AiChatRequest(
                request.scene(),
                selection.provider().getProviderCode(),
                selection.model(),
                request.messages(),
                request.temperature(),
                request.maxTokens(),
                request.stream(),
                request.responseFormat()
        );
    }

    private AiProviderAdapter requireAdapter(XiaorongProperties.Provider provider, String scene) {
        return adapters.stream()
                .filter(adapter -> adapter.supports(provider.getProtocol(), scene))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Provider 协议暂不支持"));
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