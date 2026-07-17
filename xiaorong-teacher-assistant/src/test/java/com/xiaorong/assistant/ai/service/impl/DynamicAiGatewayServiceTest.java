package com.xiaorong.assistant.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.ai.adapter.AiProviderAdapter;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatResponse;
import com.xiaorong.assistant.ai.dto.AiDtos.AiMessage;
import com.xiaorong.assistant.ai.dto.AiDtos.AiStructuredRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiStructuredResponse;
import com.xiaorong.assistant.ai.user.UserAiSettingsService;
import com.xiaorong.assistant.auth.AuthContext;
import com.xiaorong.assistant.auth.model.AuthSession;
import com.xiaorong.assistant.config.XiaorongProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DynamicAiGatewayServiceTest {

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void authenticatedUserWithoutPreferenceUsesMockForEveryOperation() {
        MockAiGatewayService mockGateway = mock(MockAiGatewayService.class);
        ProviderRoutingAiGatewayService systemGateway = mock(ProviderRoutingAiGatewayService.class);
        UserAiSettingsService settings = mock(UserAiSettingsService.class);
        XiaorongProperties properties = new XiaorongProperties();
        properties.getAi().setRealEnabled(true);
        DynamicAiGatewayService gateway = gateway(mockGateway, systemGateway, settings, properties, List.of());
        AuthContext.set(session(10001L));
        when(settings.resolveRuntimeProvider(10001L)).thenReturn(Optional.empty());
        AiChatRequest chatRequest = chatRequest();
        AiStructuredRequest structuredRequest = structuredRequest();
        SseEmitter emitter = new SseEmitter();
        AiChatResponse mockChat = response("mock", "mock-chat", true);
        AiStructuredResponse mockStructured = new AiStructuredResponse("Schema", Map.of("mock", true), true);
        when(mockGateway.chat(chatRequest)).thenReturn(mockChat);
        when(mockGateway.structured(structuredRequest)).thenReturn(mockStructured);
        when(mockGateway.stream(chatRequest)).thenReturn(emitter);

        assertThat(gateway.chat(chatRequest)).isSameAs(mockChat);
        assertThat(gateway.structured(structuredRequest)).isSameAs(mockStructured);
        assertThat(gateway.stream(chatRequest)).isSameAs(emitter);
        verifyNoInteractions(systemGateway);
    }

    @Test
    void authenticatedUserPreferenceOverridesRequestedProviderAndModel() {
        MockAiGatewayService mockGateway = mock(MockAiGatewayService.class);
        ProviderRoutingAiGatewayService systemGateway = mock(ProviderRoutingAiGatewayService.class);
        UserAiSettingsService settings = mock(UserAiSettingsService.class);
        RecordingAdapter adapter = new RecordingAdapter();
        XiaorongProperties properties = new XiaorongProperties();
        DynamicAiGatewayService gateway = gateway(mockGateway, systemGateway, settings, properties, List.of(adapter));
        AuthContext.set(session(10001L));
        XiaorongProperties.Provider provider = provider("deepseek", "sk-user-secret", "selected-model");
        when(settings.resolveRuntimeProvider(10001L)).thenReturn(Optional.of(
                new UserAiSettingsService.RuntimeProviderSelection(provider, "selected-model")));

        AiChatResponse chat = gateway.chat(chatRequest());
        AiStructuredResponse structured = gateway.structured(structuredRequest());
        SseEmitter stream = gateway.stream(chatRequest());

        assertThat(chat.providerCode()).isEqualTo("deepseek");
        assertThat(adapter.seenModels).containsExactly("selected-model", "selected-model", "selected-model");
        assertThat(adapter.seenApiKeys).containsOnly("sk-user-secret");
        assertThat(structured.data()).containsEntry("answer", "ok");
        assertThat(stream).isSameAs(adapter.emitter);
        verifyNoInteractions(mockGateway, systemGateway);
    }

    @Test
    void corruptedUserConfigurationFailsClosedWithoutSystemFallback() {
        MockAiGatewayService mockGateway = mock(MockAiGatewayService.class);
        ProviderRoutingAiGatewayService systemGateway = mock(ProviderRoutingAiGatewayService.class);
        UserAiSettingsService settings = mock(UserAiSettingsService.class);
        XiaorongProperties properties = new XiaorongProperties();
        properties.getAi().setRealEnabled(true);
        DynamicAiGatewayService gateway = gateway(mockGateway, systemGateway, settings, properties, List.of());
        AuthContext.set(session(10001L));
        when(settings.resolveRuntimeProvider(10001L))
                .thenThrow(new IllegalStateException("API Key 解密失败"));

        assertThatThrownBy(() -> gateway.chat(chatRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密失败");
        verifyNoInteractions(mockGateway, systemGateway);
    }

    @Test
    void backgroundCallsUseSystemFlagWhileNeverUsingUserSettings() {
        MockAiGatewayService mockGateway = mock(MockAiGatewayService.class);
        ProviderRoutingAiGatewayService systemGateway = mock(ProviderRoutingAiGatewayService.class);
        UserAiSettingsService settings = mock(UserAiSettingsService.class);
        XiaorongProperties properties = new XiaorongProperties();
        DynamicAiGatewayService gateway = gateway(mockGateway, systemGateway, settings, properties, List.of());
        AiChatRequest request = chatRequest();
        AiChatResponse mockResponse = response("mock", "mock-chat", true);
        AiChatResponse systemResponse = response("bailian", "qwen-plus", false);
        when(mockGateway.chat(request)).thenReturn(mockResponse);
        when(systemGateway.chat(request)).thenReturn(systemResponse);

        properties.getAi().setRealEnabled(false);
        assertThat(gateway.chat(request)).isSameAs(mockResponse);
        properties.getAi().setRealEnabled(true);
        assertThat(gateway.chat(request)).isSameAs(systemResponse);

        verifyNoInteractions(settings);
    }

    private DynamicAiGatewayService gateway(
            MockAiGatewayService mockGateway,
            ProviderRoutingAiGatewayService systemGateway,
            UserAiSettingsService settings,
            XiaorongProperties properties,
            List<AiProviderAdapter> adapters
    ) {
        return new DynamicAiGatewayService(
                mockGateway, systemGateway, settings, properties, adapters, new ObjectMapper());
    }

    private AuthSession session(long userId) {
        return new AuthSession(userId, "user", "User", List.of("student"));
    }

    private AiChatRequest chatRequest() {
        return new AiChatRequest(
                "free-ask", "system-provider", "requested-model",
                List.of(new AiMessage("user", "hello")), 0.4, 200, false, null);
    }

    private AiStructuredRequest structuredRequest() {
        return new AiStructuredRequest(
                "review", "Schema", List.of(new AiMessage("user", "return json")));
    }

    private AiChatResponse response(String providerCode, String model, boolean mock) {
        return new AiChatResponse(providerCode, model, "free-ask", "ok", 0, 0, 1L, mock);
    }

    private XiaorongProperties.Provider provider(String code, String apiKey, String model) {
        XiaorongProperties.Provider provider = new XiaorongProperties.Provider();
        provider.setProviderCode(code);
        provider.setProtocol("openai-compatible");
        provider.setBaseUrl("https://example.com/v1");
        provider.setApiKey(apiKey);
        provider.setDefaultModel(model);
        provider.setEnabled(true);
        return provider;
    }

    private static final class RecordingAdapter implements AiProviderAdapter {
        private final java.util.ArrayList<String> seenModels = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> seenApiKeys = new java.util.ArrayList<>();
        private final SseEmitter emitter = new SseEmitter();

        @Override
        public String providerCode() {
            return "openai-compatible";
        }

        @Override
        public boolean supports(String protocol, String scene) {
            return "openai-compatible".equals(protocol);
        }

        @Override
        public AiChatResponse chat(AiChatRequest request, XiaorongProperties.Provider provider) {
            remember(request, provider);
            String content = "json_object".equals(request.responseFormat()) ? "{\"answer\":\"ok\"}" : "ok";
            return new AiChatResponse(provider.getProviderCode(), request.model(), request.scene(),
                    content, 0, 0, 1L, false);
        }

        @Override
        public SseEmitter stream(AiChatRequest request, XiaorongProperties.Provider provider) {
            remember(request, provider);
            return emitter;
        }

        private void remember(AiChatRequest request, XiaorongProperties.Provider provider) {
            seenModels.add(request.model());
            seenApiKeys.add(provider.getApiKey());
        }
    }
}