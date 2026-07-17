package com.xiaorong.assistant.study.service.impl;

import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatResponse;
import com.xiaorong.assistant.ai.dto.AiDtos.AiStructuredRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiStructuredResponse;
import com.xiaorong.assistant.ai.service.AiGatewayService;
import com.xiaorong.assistant.auth.AuthContext;
import com.xiaorong.assistant.auth.model.AuthSession;
import com.xiaorong.assistant.study.ai.AiTokenBudgetService;
import com.xiaorong.assistant.study.ai.InterviewFollowUpPolicy;
import com.xiaorong.assistant.study.ai.StudyAiConversationService;
import com.xiaorong.assistant.study.ai.StudyOverviewAggregator;
import com.xiaorong.assistant.study.content.StudyTemplateProvider;
import com.xiaorong.assistant.study.dto.StudyDtos.AdviceResponse;
import com.xiaorong.assistant.study.dto.StudyDtos.AskRequest;
import com.xiaorong.assistant.study.dto.StudyDtos.AskResponse;
import com.xiaorong.assistant.study.dto.StudyDtos.CreateSessionRequest;
import com.xiaorong.assistant.study.dto.StudyDtos.LessonNode;
import com.xiaorong.assistant.study.dto.StudyDtos.NodeSubmitResponse;
import com.xiaorong.assistant.study.dto.StudyDtos.ReviewResponse;
import com.xiaorong.assistant.study.dto.StudyDtos.SessionCreateResponse;
import com.xiaorong.assistant.study.dto.StudyDtos.SubmitAnswerRequest;
import com.xiaorong.assistant.study.service.StudyScoringService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockStudyServiceTest {
    private RecordingGateway gateway;
    private MockStudyService service;

    @BeforeEach
    void setUp() {
        AuthContext.set(new AuthSession(9L, "student", "Student", List.of("USER")));
        gateway = new RecordingGateway(List.of(
                "Real answer from configured provider",
                "1. Review Proxy mistakes\n2. Explain toRefs boundaries\n3. Finish one targeted exercise",
                "1. Retry the low score node\n2. Summarize the key concepts\n3. Ask one follow-up question"
        ));
        StudyAiConversationService conversation = new StudyAiConversationService(
                gateway, new AiTokenBudgetService(), Runnable::run);
        service = new MockStudyService(new StudyTemplateProvider(""), new StudyScoringService(), conversation,
                new StudyOverviewAggregator(), new InterviewFollowUpPolicy());
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void mockModeKeepsFreeAskOutOfScoresAndPropagatesRuntimeMetadataAndAiAdvice() {
        SessionCreateResponse session = service.createSession(new CreateSessionRequest(1L, "quick"));
        LessonNode scoredNode = service.getScript(session.sessionId()).nodes().stream()
                .filter(node -> node.answerKeywords() != null && !node.answerKeywords().isEmpty())
                .findFirst()
                .orElseThrow();
        NodeSubmitResponse submitted = service.submitNode(session.sessionId(), scoredNode.nodeId(),
                new SubmitAnswerRequest(String.join(" ", scoredNode.answerKeywords())));

        AskResponse ask = service.ask(session.sessionId(), new AskRequest(scoredNode.nodeId(), "Why?"));
        AdviceResponse advice = service.getAdvice();
        ReviewResponse review = service.getReview(session.sessionId());

        assertThat(ask.answer()).isEqualTo("Real answer from configured provider");
        assertThat(ask.degraded()).isFalse();
        assertThat(ask.providerCode()).isEqualTo("deepseek");
        assertThat(ask.model()).isEqualTo("deepseek-chat");
        assertThat(advice.averageScore()).isEqualTo(submitted.score());
        assertThat(advice.hasLearningData()).isTrue();
        assertThat(advice.suggestions()).containsExactly(
                "Review Proxy mistakes", "Explain toRefs boundaries", "Finish one targeted exercise");
        assertThat(advice.degraded()).isFalse();
        assertThat(advice.providerCode()).isEqualTo("deepseek");
        assertThat(advice.model()).isEqualTo("deepseek-chat");
        assertThat(review.averageScore()).isEqualTo(submitted.score());
        assertThat(review.nextActions()).containsExactly(
                "Retry the low score node", "Summarize the key concepts", "Ask one follow-up question");
        assertThat(review.degraded()).isFalse();
        assertThat(review.providerCode()).isEqualTo("deepseek");
        assertThat(review.model()).isEqualTo("deepseek-chat");
        assertThat(gateway.requests).hasSize(3);
    }

    private static final class RecordingGateway implements AiGatewayService {
        private final List<AiChatRequest> requests = new ArrayList<>();
        private final List<String> contents;

        private RecordingGateway(List<String> contents) {
            this.contents = contents;
        }

        @Override
        public AiChatResponse chat(AiChatRequest request) {
            requests.add(request);
            String content = contents.get(Math.min(requests.size() - 1, contents.size() - 1));
            return new AiChatResponse("deepseek", "deepseek-chat", request.scene(), content, 20, 30, 2L, false);
        }

        @Override
        public AiStructuredResponse structured(AiStructuredRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SseEmitter stream(AiChatRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
