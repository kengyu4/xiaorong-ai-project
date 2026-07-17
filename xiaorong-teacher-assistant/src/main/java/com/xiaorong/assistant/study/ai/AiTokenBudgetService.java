package com.xiaorong.assistant.study.ai;

import com.xiaorong.assistant.ai.dto.AiDtos.AiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiTokenBudgetService {
    private static final Logger log = LoggerFactory.getLogger(AiTokenBudgetService.class);
    private static final Map<String, Integer> SCENE_LIMITS = Map.of(
            "free-ask", 1000,
            "deep-review", 800,
            "interview-follow-up", 600,
            "personal-review", 700
    );
    private final long warningLimit;
    private final long hardLimit;
    private final Map<String, MutableUsage> usage = new ConcurrentHashMap<>();

    public AiTokenBudgetService() {
        this(20_000, 30_000);
    }

    AiTokenBudgetService(long warningLimit, long hardLimit) {
        this.warningLimit = warningLimit;
        this.hardLimit = hardLimit;
    }

    public BudgetDecision reserve(long userId, String scene, List<AiMessage> messages) {
        MutableUsage current = current(userId);
        int maxTokens = SCENE_LIMITS.getOrDefault(scene, 600);
        long totalTokens = current.totalTokens();
        if (totalTokens >= hardLimit) {
            current.degradedRequests++;
            log.warn("AI token budget exhausted: userId={}, scene={}, totalTokens={}, hardLimit={}",
                    userId, scene, totalTokens, hardLimit);
            return new BudgetDecision(false, true, maxTokens, List.of());
        }
        current.requests++;
        boolean warning = totalTokens >= warningLimit;
        if (warning && !current.warningLogged) {
            current.warningLogged = true;
            log.warn("AI token budget warning: userId={}, scene={}, totalTokens={}, warningLimit={}",
                    userId, scene, totalTokens, warningLimit);
        }
        return new BudgetDecision(true, warning, maxTokens, trim(messages));
    }

    public void complete(long userId, String scene, Integer promptTokens, Integer completionTokens, boolean degraded) {
        MutableUsage current = current(userId);
        current.promptTokens += safe(promptTokens);
        current.completionTokens += safe(completionTokens);
        if (degraded) current.degradedRequests++;
    }

    public BudgetStatus status(long userId) {
        MutableUsage value = current(userId);
        long total = value.totalTokens();
        return new BudgetStatus(LocalDate.now().toString(), value.requests, value.promptTokens,
                value.completionTokens, total, value.degradedRequests, total >= warningLimit, total >= hardLimit,
                warningLimit, hardLimit);
    }

    private MutableUsage current(long userId) {
        return usage.computeIfAbsent(LocalDate.now() + ":" + userId, ignored -> new MutableUsage());
    }

    private List<AiMessage> trim(List<AiMessage> messages) {
        if (messages == null || messages.isEmpty()) return List.of();
        int remaining = 1800;
        ArrayList<AiMessage> reversed = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0 && remaining > 0; i--) {
            AiMessage message = messages.get(i);
            String content = message.content() == null ? "" : message.content();
            if (content.length() > remaining) content = content.substring(content.length() - remaining);
            reversed.add(new AiMessage(message.role(), content));
            remaining -= content.length();
        }
        java.util.Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private int safe(Integer value) { return value == null || value < 0 ? 0 : value; }

    public record BudgetDecision(boolean allowed, boolean warning, int maxTokens, List<AiMessage> messages) {}
    public record BudgetStatus(String date, long requests, long promptTokens, long completionTokens, long totalTokens,
                               long degradedRequests, boolean warning, boolean exhausted, long warningLimit, long hardLimit) {}
    private static final class MutableUsage {
        long requests; long promptTokens; long completionTokens; long degradedRequests; boolean warningLogged;
        long totalTokens() { return promptTokens + completionTokens; }
    }
}