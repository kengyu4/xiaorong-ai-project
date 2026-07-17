package com.xiaorong.assistant.ai.adapter;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.config.XiaorongProperties;
import org.springframework.stereotype.Component;

@Component
public class DefaultDashScopeChatModelFactory implements DashScopeChatModelFactory {

    @Override
    public DashScopeChatModel create(AiChatRequest request, XiaorongProperties.Provider provider) {
        requireApiKey(provider);
        String model = selectedModel(request, provider);

        DashScopeApi.Builder apiBuilder = DashScopeApi.builder().apiKey(provider.getApiKey());
        String baseUrl = nativeBaseUrl(provider.getBaseUrl());
        if (baseUrl != null) {
            apiBuilder.baseUrl(baseUrl);
        }

        DashScopeChatOptions.DashscopeChatOptionsBuilder options = DashScopeChatOptions.builder()
                .withModel(model)
                .withStream(Boolean.TRUE.equals(request.stream()))
                .withIncrementalOutput(Boolean.TRUE.equals(request.stream()));
        if (request.maxTokens() != null && request.maxTokens() > 0) {
            options.withMaxToken(request.maxTokens());
        }
        if (request.temperature() != null) {
            options.withTemperature(request.temperature());
        }
        if ("json_object".equalsIgnoreCase(request.responseFormat())) {
            options.withResponseFormat(new DashScopeResponseFormat(DashScopeResponseFormat.Type.JSON_OBJECT));
        }

        return DashScopeChatModel.builder()
                .dashScopeApi(apiBuilder.build())
                .defaultOptions(options.build())
                .build();
    }

    private String selectedModel(AiChatRequest request, XiaorongProperties.Provider provider) {
        String selected = request.model();
        if (selected == null || selected.isBlank()) {
            selected = provider.getDefaultModel();
        }
        if (selected == null || selected.isBlank()) {
            throw new IllegalArgumentException("\u6a21\u578b\u672a\u914d\u7f6e");
        }
        return selected.trim();
    }

    private void requireApiKey(XiaorongProperties.Provider provider) {
        if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            throw new IllegalStateException("\u767e\u70bc API Key \u672a\u914d\u7f6e");
        }
    }

    private String nativeBaseUrl(String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()) {
            return null;
        }
        String normalized = configuredBaseUrl.trim();
        normalized = normalized.replaceFirst("/compatible-mode/v1/?$", "");
        normalized = normalized.replaceFirst("/api/v1/?$", "");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? null : normalized;
    }
}
