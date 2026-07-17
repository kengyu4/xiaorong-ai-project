package com.xiaorong.assistant.study.ai;

import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatResponse;
import com.xiaorong.assistant.ai.dto.AiDtos.AiStructuredRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiStructuredResponse;
import com.xiaorong.assistant.ai.service.AiGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

class StudyAiConversationServiceTest {
    @Test
    void freeAskUsesThreeRoundHistoryOneThousandTokensAndSixHundredCharacterPrompt() {
        RecordingGateway gateway = new RecordingGateway();
        StudyAiConversationService service = service(gateway);

        for (int i = 1; i <= 5; i++) {
            service.ask(9L, 101L, "node-1", "Vue 响应式", "问题" + i, List.of("Proxy", "Reflect"));
        }

        AiChatRequest request = gateway.requests.get(4);
        assertThat(request.maxTokens()).isEqualTo(1000);
        assertThat(request.messages()).hasSize(8);
        assertThat(request.messages().get(0).content()).contains("\u5c0f\u7ed2\u8001\u5e08", "\u5148\u7ed9\u7ed3\u8bba", "\u4e0d\u8bf4\u5e9f\u8bdd", "3\uff5e5\u53e5");
        assertThat(request.messages().stream().map(message -> message.content()).toList())
                .noneMatch(content -> content.contains("问题1"))
                .anyMatch(content -> content.contains("问题2"))
                .anyMatch(content -> content.contains("问题5"));
    }

    @Test
    void askRemovesMarkdownEmphasisMarkersFromAiAnswer() {
        RecordingGateway gateway = new RecordingGateway();
        gateway.contents.add("**Proxy** ???????????");
        StudyAiConversationService service = service(gateway);

        StudyAiConversationService.ConversationAnswer answer = service.ask(
                9L, 101L, "node-1", "Vue ???", "Proxy ???", List.of("Proxy"));

        assertThat(answer.answer()).isEqualTo("Proxy ???????????");
        assertThat(answer.answer()).doesNotContain("**", "****");
    }

    @Test
    void askReturnsOnlySafeRuntimeMetadata() {
        RecordingGateway gateway = new RecordingGateway();
        gateway.providerCode = "deepseek";
        gateway.model = "deepseek-chat";
        gateway.mock = false;
        StudyAiConversationService service = service(gateway);

        StudyAiConversationService.ConversationAnswer answer = service.ask(
                9L, 101L, "node-1", "Vue 响应式", "Proxy 是什么", List.of("Proxy"));

        assertThat(answer.degraded()).isFalse();
        assertThat(answer.providerCode()).isEqualTo("deepseek");
        assertThat(answer.model()).isEqualTo("deepseek-chat");
        assertThat(answer.toString()).doesNotContain("apiKey", "secret", "Bearer");
    }

    @Test
    void sseDoneEventContainsSafeRuntimeMetadata() throws Exception {
        RecordingGateway gateway = new RecordingGateway();
        gateway.providerCode = "bailian";
        gateway.model = "qwen-plus";
        gateway.mock = false;
        StreamController controller = new StreamController(service(gateway));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        MvcResult started = mvc.perform(MockMvcRequestBuilders.get("/test-stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(content().string(containsString("event:ready")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(containsString("\"providerCode\":\"bailian\"")))
                .andExpect(content().string(containsString("\"model\":\"qwen-plus\"")))
                .andExpect(content().string(containsString("\"degraded\":false")));
    }

    @Test
    void sseForwardsUpstreamDeltasWithoutCallingChatAndUsesConciseXiaorongPrompt() throws Exception {
        RecordingGateway gateway = new RecordingGateway();
        gateway.providerCode = "deepseek";
        gateway.model = "deepseek-chat";
        gateway.mock = false;
        gateway.streamChunks = List.of("*", "*Proxy", " ???*", "*");
        StreamController controller = new StreamController(service(gateway));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        MvcResult started = mvc.perform(MockMvcRequestBuilders.get("/test-stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(content().string(containsString("Proxy")))
                .andExpect(content().string(containsString(" ???")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("**"))));

        assertThat(gateway.chatCalls).isZero();
        assertThat(gateway.streamRequests).hasSize(1);
        assertThat(gateway.streamRequests.get(0).messages().get(0).content())
                .contains("\u5c0f\u7ed2\u8001\u5e08", "\u5148\u7ed9\u7ed3\u8bba", "\u4e0d\u8bf4\u5e9f\u8bdd", "3\uff5e5\u53e5");
    }

    @Test
    void personalizedAdviceParsesTwoToThreeActionsCachesByFingerprintAndReturnsMetadata() {
        RecordingGateway gateway = new RecordingGateway();
        gateway.contents.add("1. 重刷 Proxy 低分题\n2. 用三句话复述响应式边界\n3. 完成 Vue 响应式课程练习");
        gateway.contents.add("1. 复习 toRefs\n2. 再做一道题");
        gateway.providerCode = "deepseek";
        gateway.model = "deepseek-chat";
        gateway.mock = false;
        StudyAiConversationService service = service(gateway);

        StudyAiConversationService.PersonalizedAdvice first = service.personalizedAdvice(
                9L, "fingerprint-a", List.of("Vue 响应式"), 68, List.of("Proxy", "toRefs"));
        StudyAiConversationService.PersonalizedAdvice cached = service.personalizedAdvice(
                9L, "fingerprint-a", List.of("Vue 响应式"), 68, List.of("Proxy", "toRefs"));
        StudyAiConversationService.PersonalizedAdvice changed = service.personalizedAdvice(
                9L, "fingerprint-b", List.of("Vue 响应式"), 72, List.of("toRefs"));

        assertThat(first.suggestions()).containsExactly(
                "重刷 Proxy 低分题", "用三句话复述响应式边界", "完成 Vue 响应式课程练习");
        assertThat(first.providerCode()).isEqualTo("deepseek");
        assertThat(first.model()).isEqualTo("deepseek-chat");
        assertThat(first.degraded()).isFalse();
        assertThat(cached).isSameAs(first);
        assertThat(changed.suggestions()).containsExactly("复习 toRefs", "再做一道题");
        assertThat(gateway.requests).hasSize(2);
        assertThat(gateway.requests.get(0).scene()).isEqualTo("personal-review");
        assertThat(gateway.requests.get(0).maxTokens()).isEqualTo(700);
    }

    @Test
    void interviewOnlyChangesItsLimitToSixHundred() {
        RecordingGateway gateway = new RecordingGateway();
        StudyAiConversationService service = service(gateway);

        service.interviewFollowUp(9L, "Proxy 是什么", "拦截操作", List.of("Proxy"), 1);

        assertThat(gateway.requests.get(0).scene()).isEqualTo("interview-follow-up");
        assertThat(gateway.requests.get(0).maxTokens()).isEqualTo(600);
    }

    @Test
    void deepReviewExposesCompletedRuntimeMetadataAndFailedState() {
        RecordingGateway successGateway = new RecordingGateway();
        successGateway.providerCode = "deepseek";
        successGateway.model = "deepseek-chat";
        successGateway.mock = false;
        StudyAiConversationService successService = service(successGateway);

        String completedId = successService.startDeepReview(9L, 101L, "Proxy 是什么", "不知道",
                List.of(), List.of("Proxy"), "Proxy 拦截对象操作");
        StudyAiConversationService.DeepReviewTask completed = successService.getDeepReview(9L, 101L, completedId);

        assertThat(completed.status()).isEqualTo("completed");
        assertThat(completed.providerCode()).isEqualTo("deepseek");
        assertThat(completed.model()).isEqualTo("deepseek-chat");
        assertThat(successGateway.requests.get(0).maxTokens()).isEqualTo(800);

        RecordingGateway failedGateway = new RecordingGateway();
        failedGateway.failure = new IllegalStateException("upstream failed with sk-never-expose");
        StudyAiConversationService failedService = service(failedGateway);
        String failedId = failedService.startDeepReview(9L, 102L, "Proxy 是什么", "不知道",
                List.of(), List.of("Proxy"), "Proxy 拦截对象操作");
        StudyAiConversationService.DeepReviewTask failed = failedService.getDeepReview(9L, 102L, failedId);

        assertThat(failed.status()).isEqualTo("failed");
        assertThat(failed.degraded()).isTrue();
        assertThat(failed.content()).doesNotContain("sk-never-expose", "upstream failed");
        assertThat(failed.providerCode()).isNull();
        assertThat(failed.model()).isNull();
        assertThatThrownBy(() -> failedService.getDeepReview(10L, 102L, failedId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private StudyAiConversationService service(RecordingGateway gateway) {
        return new StudyAiConversationService(gateway, new AiTokenBudgetService(20_000, 30_000), Runnable::run);
    }

    @RestController
    private static final class StreamController {
        private final StudyAiConversationService service;

        private StreamController(StudyAiConversationService service) { this.service = service; }

        @GetMapping(value = "/test-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter stream() {
            return service.streamAsk(9L, 101L, "node-1", "Vue 响应式", "Proxy 是什么");
        }
    }

    private static final class RecordingGateway implements AiGatewayService {
        private final List<AiChatRequest> requests = new ArrayList<>();
        private final List<String> contents = new ArrayList<>();
        private String providerCode = "mock";
        private String model = "mock";
        private boolean mock = true;
        private RuntimeException failure;
        private final List<AiChatRequest> streamRequests = new ArrayList<>();
        private List<String> streamChunks = List.of("AI??");
        private int chatCalls;

        @Override
        public AiChatResponse chat(AiChatRequest request) {
            chatCalls++;
            requests.add(request);
            if (failure != null) throw failure;
            String content = requests.size() <= contents.size() ? contents.get(requests.size() - 1) : "AI回答" + requests.size();
            return new AiChatResponse(providerCode, model, request.scene(), content, 20, 30, 1L, mock);
        }

        @Override
        public AiStructuredResponse structured(AiStructuredRequest request) { throw new UnsupportedOperationException(); }

        @Override
        public SseEmitter stream(AiChatRequest request) { throw new UnsupportedOperationException(); }

        @Override
        public void stream(AiChatRequest request, AiStreamListener listener) {
            streamRequests.add(request);
            if (failure != null) {
                listener.onError(failure);
                return;
            }
            for (String chunk : streamChunks) {
                listener.onDelta(chunk);
            }
            listener.onComplete(new AiChatResponse(providerCode, model, request.scene(), "",
                    20, 30, 1L, mock));
        }
    }
}
