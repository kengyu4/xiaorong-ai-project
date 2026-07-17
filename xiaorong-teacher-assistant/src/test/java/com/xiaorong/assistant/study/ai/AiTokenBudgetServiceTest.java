package com.xiaorong.assistant.study.ai;

import com.xiaorong.assistant.ai.dto.AiDtos.AiMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiTokenBudgetServiceTest {
    @Test
    void doublesEveryVisibleAiSceneLimitAndStillEnforcesDailyBudget() {
        AiTokenBudgetService service = new AiTokenBudgetService(700, 800);
        List<AiMessage> oversized = List.of(new AiMessage("user", "x".repeat(4000)));

        assertThat(service.reserve(7L, "free-ask", oversized).maxTokens()).isEqualTo(1000);
        assertThat(service.reserve(8L, "deep-review", oversized).maxTokens()).isEqualTo(800);
        assertThat(service.reserve(9L, "personal-review", oversized).maxTokens()).isEqualTo(700);
        assertThat(service.reserve(10L, "interview-follow-up", oversized).maxTokens()).isEqualTo(600);
        assertThat(service.reserve(11L, "other-ai-scene", oversized).maxTokens()).isEqualTo(600);

        AiTokenBudgetService.BudgetDecision first = service.reserve(7L, "free-ask", oversized);
        assertThat(first.messages().stream().map(AiMessage::content).mapToInt(String::length).sum())
                .isLessThanOrEqualTo(1800);
        service.complete(7L, "free-ask", 600, 150, false);

        assertThat(service.reserve(7L, "free-ask", oversized).warning()).isTrue();
        service.complete(7L, "free-ask", 50, 50, false);

        assertThat(service.reserve(7L, "free-ask", oversized).allowed()).isFalse();
        assertThat(service.status(7L).degradedRequests()).isEqualTo(1);
    }
}
