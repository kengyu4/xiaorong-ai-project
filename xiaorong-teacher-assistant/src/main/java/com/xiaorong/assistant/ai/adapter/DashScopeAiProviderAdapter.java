package com.xiaorong.assistant.ai.adapter;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatResponse;
import com.xiaorong.assistant.ai.dto.AiDtos.AiMessage;
import com.xiaorong.assistant.ai.service.AiGatewayService.AiStreamListener;
import com.xiaorong.assistant.config.XiaorongProperties;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class DashScopeAiProviderAdapter implements AiProviderAdapter {
    private final DashScopeChatModelFactory modelFactory;

    public DashScopeAiProviderAdapter(DashScopeChatModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    @Override
    public String providerCode() {
        return "dashscope";
    }

    @Override
    public boolean supports(String protocol, String scene) {
        return "dashscope".equalsIgnoreCase(protocol);
    }

    @Override
    public AiChatResponse chat(AiChatRequest request, XiaorongProperties.Provider provider) {
        long startedAt = System.currentTimeMillis();
        String selectedModel = selectedModel(request, provider);
        try {
            ChatResponse response = modelFactory.create(request, provider).call(prompt(request));
            return response(request, provider, response, content(response), selectedModel,
                    System.currentTimeMillis() - startedAt);
        } catch (RuntimeException ex) {
            throw providerRequestFailure(provider);
        }
    }

    @Override
    public List<String> listModels(XiaorongProperties.Provider provider) {
        String model = provider.getDefaultModel();
        return model == null || model.isBlank() ? List.of() : List.of(model.trim());
    }

    @Override
    public void stream(AiChatRequest request, XiaorongProperties.Provider provider, AiStreamListener listener) {
        long startedAt = System.currentTimeMillis();
        String selectedModel = selectedModel(request, provider);
        StringBuilder answer = new StringBuilder();
        AtomicReference<ChatResponse> latest = new AtomicReference<>();
        AtomicBoolean terminal = new AtomicBoolean(false);
        DashScopeChatModel model;
        try {
            model = modelFactory.create(request, provider);
        } catch (RuntimeException ex) {
            listener.onError(providerRequestFailure(provider));
            return;
        }

        model.stream(prompt(request)).subscribe(
                response -> {
                    if (terminal.get()) return;
                    latest.set(response);
                    String chunk = content(response);
                    if (chunk == null || chunk.isEmpty()) return;
                    synchronized (answer) {
                        answer.append(chunk);
                    }
                    listener.onDelta(chunk);
                },
                cause -> {
                    if (terminal.compareAndSet(false, true)) {
                        listener.onError(providerRequestFailure(provider));
                    }
                },
                () -> {
                    if (!terminal.compareAndSet(false, true)) return;
                    String completed;
                    synchronized (answer) {
                        completed = answer.toString();
                    }
                    listener.onComplete(response(request, provider, latest.get(), completed, selectedModel,
                            System.currentTimeMillis() - startedAt));
                }
        );
    }

    private Prompt prompt(AiChatRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.messages() != null) {
            for (AiMessage message : request.messages()) {
                String role = message.role() == null ? "user" : message.role().trim().toLowerCase();
                String content = message.content() == null ? "" : message.content();
                messages.add(switch (role) {
                    case "system" -> new SystemMessage(content);
                    case "assistant" -> new AssistantMessage(content);
                    default -> new UserMessage(content);
                });
            }
        }
        if (messages.isEmpty()) {
            messages.add(new UserMessage("??"));
        }
        return new Prompt(messages);
    }

    private AiChatResponse response(AiChatRequest request, XiaorongProperties.Provider provider,
                                    ChatResponse response, String content, String fallbackModel, long latencyMs) {
        ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        String model = metadata == null || metadata.getModel() == null || metadata.getModel().isBlank()
                ? fallbackModel : metadata.getModel();
        return new AiChatResponse(
                provider.getProviderCode(),
                model,
                request.scene(),
                content == null ? "" : content,
                usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens(),
                latencyMs,
                false
        );
    }

    private String content(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private String selectedModel(AiChatRequest request, XiaorongProperties.Provider provider) {
        String model = request.model();
        if (model == null || model.isBlank()) {
            model = provider.getDefaultModel();
        }
        return model == null ? "" : model.trim();
    }

    private IllegalStateException providerRequestFailure(XiaorongProperties.Provider provider) {
        return new IllegalStateException("Provider " + provider.getProviderCode() + " \u8c03\u7528\u5931\u8d25");
    }
}
