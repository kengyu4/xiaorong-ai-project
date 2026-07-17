package com.xiaorong.assistant.ai.adapter;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatResponse;
import com.xiaorong.assistant.ai.dto.AiDtos.AiMessage;
import com.xiaorong.assistant.ai.service.AiGatewayService.AiStreamListener;
import com.xiaorong.assistant.config.XiaorongProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashScopeAiProviderAdapterTest {

    @Test
    void streamsDashScopeFluxDeltasWithoutBlockingTheControllerThread() throws Exception {
        DashScopeChatModel model = mock(DashScopeChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(
                response("小绒"), response("老师")
        ).delayElements(Duration.ofMillis(200)));
        AtomicReference<AiChatRequest> capturedRequest = new AtomicReference<>();
        AtomicReference<XiaorongProperties.Provider> capturedProvider = new AtomicReference<>();
        DashScopeChatModelFactory factory = (request, provider) -> {
            capturedRequest.set(request);
            capturedProvider.set(provider);
            return model;
        };
        DashScopeAiProviderAdapter adapter = new DashScopeAiProviderAdapter(factory);
        XiaorongProperties.Provider provider = provider();
        AiChatRequest request = request();
        List<String> chunks = new ArrayList<>();
        AtomicReference<AiChatResponse> completed = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        long startedAt = System.nanoTime();
        adapter.stream(request, provider, new AiStreamListener() {
            @Override
            public void onDelta(String text) { chunks.add(text); }

            @Override
            public void onComplete(AiChatResponse response) {
                completed.set(response);
                done.countDown();
            }

            @Override
            public void onError(Throwable cause) {
                done.countDown();
                throw new AssertionError(cause);
            }
        });
        long returnMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertThat(returnMillis).isLessThan(150L);
        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(chunks).containsExactly("小绒", "老师");
        assertThat(completed.get()).isNotNull();
        assertThat(completed.get().providerCode()).isEqualTo("bailian");
        assertThat(completed.get().model()).isEqualTo("qwen-plus");
        assertThat(capturedRequest.get()).isSameAs(request);
        assertThat(capturedProvider.get()).isSameAs(provider);

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).stream(promptCaptor.capture());
        List<Message> messages = promptCaptor.getValue().getInstructions();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(0).getText()).contains("小绒老师");
        assertThat(messages.get(1).getText()).isEqualTo("Proxy 是什么");
    }

    @Test
    void exposesReadableSafeErrorsWithoutQuestionMarkPlaceholders() {
        XiaorongProperties.Provider provider = provider();
        provider.setApiKey(" ");
        DefaultDashScopeChatModelFactory factory = new DefaultDashScopeChatModelFactory();

        assertThatThrownBy(() -> factory.create(request(), provider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("\u767e\u70bc API Key \u672a\u914d\u7f6e");

        DashScopeAiProviderAdapter adapter = new DashScopeAiProviderAdapter((request, configuredProvider) -> {
            throw new IllegalStateException("upstream secret must stay hidden");
        });
        provider.setApiKey("sk-user-secret");

        assertThatThrownBy(() -> adapter.chat(request(), provider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Provider bailian \u8c03\u7528\u5931\u8d25")
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining("?");
    }

    @Test
    void rejectsMissingModelWithReadableMessage() {
        XiaorongProperties.Provider provider = provider();
        provider.setDefaultModel(" ");
        AiChatRequest requestWithoutModel = new AiChatRequest(
                "free-ask", "bailian", " ", request().messages(), 0.3, 1000, true, null);

        assertThatThrownBy(() -> new DefaultDashScopeChatModelFactory().create(requestWithoutModel, provider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u6a21\u578b\u672a\u914d\u7f6e")
                .hasMessageNotContaining("?");
    }

    @Test
    void supportsOnlyDashScopeProtocol() {
        DashScopeAiProviderAdapter adapter = new DashScopeAiProviderAdapter((request, provider) -> mock(DashScopeChatModel.class));

        assertThat(adapter.supports("dashscope", "free-ask")).isTrue();
        assertThat(adapter.supports("openai-compatible", "free-ask")).isFalse();
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage(text))));
    }

    private static AiChatRequest request() {
        return new AiChatRequest("free-ask", "bailian", "qwen-plus", List.of(
                new AiMessage("system", "你是小绒老师，回答简洁。"),
                new AiMessage("user", "Proxy 是什么")
        ), 0.3, 1000, true, null);
    }

    private static XiaorongProperties.Provider provider() {
        XiaorongProperties.Provider provider = new XiaorongProperties.Provider();
        provider.setProviderCode("bailian");
        provider.setProviderName("阿里百炼");
        provider.setProtocol("dashscope");
        provider.setBaseUrl("https://dashscope.aliyuncs.com");
        provider.setApiKey("sk-user-secret");
        provider.setDefaultModel("qwen-plus");
        provider.setSupportStream(true);
        provider.setEnabled(true);
        return provider;
    }
}
