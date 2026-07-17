package com.xiaorong.assistant.study.ai;

import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatResponse;
import com.xiaorong.assistant.ai.dto.AiDtos.AiMessage;
import com.xiaorong.assistant.ai.service.AiGatewayService;
import com.xiaorong.assistant.ai.service.AiGatewayService.AiStreamListener;
import com.xiaorong.assistant.ai.text.AiAnswerSanitizer;
import com.xiaorong.assistant.auth.AuthContext;
import com.xiaorong.assistant.auth.model.AuthSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Service
public class StudyAiConversationService {
    private static final Pattern LIST_PREFIX = Pattern.compile(
            "^\\s*(?:(?:[-*•])|(?:\\d{1,2}[.、:：)）]))\\s*");
    private static final Pattern PUBLIC_METADATA = Pattern.compile("[A-Za-z0-9._:/-]+");

    private final AiGatewayService gateway;
    private final AiTokenBudgetService budgets;
    private final Executor executor;
    private final Map<String, ArrayDeque<AiMessage>> histories = new ConcurrentHashMap<>();
    private final Map<String, DeepReviewTask> reviewTasks = new ConcurrentHashMap<>();
    private final Map<Long, CachedAdvice> adviceCache = new ConcurrentHashMap<>();
    private final Map<Long, Object> adviceLocks = new ConcurrentHashMap<>();

    @Autowired
    public StudyAiConversationService(AiGatewayService gateway, AiTokenBudgetService budgets) {
        this(gateway, budgets, ForkJoinPool.commonPool());
    }

    public StudyAiConversationService(AiGatewayService gateway, AiTokenBudgetService budgets, Executor executor) {
        this.gateway = gateway;
        this.budgets = budgets;
        this.executor = executor;
    }

    public ConversationAnswer ask(long userId, long sessionId, String nodeId, String knowledgePoint,
                                  String question, List<String> relatedKeywords) {
        String key = historyKey(userId, sessionId, nodeId);
        AiTokenBudgetService.BudgetDecision budget = budgets.reserve(
                userId, "free-ask", freeAskMessages(key, knowledgePoint, question));
        if (!budget.allowed()) {
            return new ConversationAnswer(fallbackAsk(knowledgePoint), safeList(relatedKeywords), true, null, null);
        }
        try {
            AiChatResponse response = gateway.chat(request("free-ask", budget.messages(), budget.maxTokens()));
            String answer = safeContent(response.content(), fallbackAsk(knowledgePoint));
            appendRound(key, question, answer);
            RuntimeMetadata metadata = runtimeMetadata(response);
            budgets.complete(userId, "free-ask", response.promptTokens(), response.completionTokens(), metadata.degraded());
            return new ConversationAnswer(answer, safeList(relatedKeywords), metadata.degraded(),
                    metadata.providerCode(), metadata.model());
        } catch (RuntimeException ex) {
            budgets.complete(userId, "free-ask", 0, 0, true);
            return new ConversationAnswer(fallbackAsk(knowledgePoint), safeList(relatedKeywords), true, null, null);
        }
    }

    public SseEmitter streamAsk(long userId, long sessionId, String nodeId, String knowledgePoint, String question) {
        String key = historyKey(userId, sessionId, nodeId);
        AiTokenBudgetService.BudgetDecision budget = budgets.reserve(
                userId, "free-ask", freeAskMessages(key, knowledgePoint, question));
        if (!budget.allowed()) {
            return fallbackEmitter(fallbackAsk(knowledgePoint));
        }

        SseEmitter emitter = new SseEmitter(60_000L);
        StringBuilder answer = new StringBuilder();
        AiAnswerSanitizer.Stream sanitizer = AiAnswerSanitizer.stream();
        AtomicBoolean terminal = new AtomicBoolean(false);
        try {
            emitter.send(SseEmitter.event()
                    .name("ready")
                    .reconnectTime(3_000L)
                    .data(Map.of("status", "connected")));
            gateway.stream(streamRequest("free-ask", budget.messages(), budget.maxTokens()), new AiStreamListener() {
                @Override
                public void onDelta(String text) {
                    if (terminal.get() || text == null || text.isEmpty()) return;
                    String cleaned = sanitizer.accept(text);
                    if (cleaned.isEmpty()) return;
                    try {
                        synchronized (answer) {
                            answer.append(cleaned);
                        }
                        emitter.send(SseEmitter.event().name("delta").data(Map.of("text", cleaned)));
                    } catch (Exception ex) {
                        completeStreamWithFallback(emitter, terminal, answer, key, question, knowledgePoint, userId);
                    }
                }

                @Override
                public void onComplete(AiChatResponse response) {
                    if (!terminal.compareAndSet(false, true)) return;
                    try {
                        String tail = sanitizer.finish();
                        if (!tail.isEmpty()) {
                            synchronized (answer) {
                                answer.append(tail);
                            }
                            emitter.send(SseEmitter.event().name("delta").data(Map.of("text", tail)));
                        }
                        String completedAnswer;
                        synchronized (answer) {
                            completedAnswer = answer.toString();
                        }
                        if (completedAnswer.isBlank()) {
                            completedAnswer = fallbackAsk(knowledgePoint);
                            emitter.send(SseEmitter.event().name("delta").data(Map.of("text", completedAnswer)));
                        }
                        appendRound(key, question, completedAnswer);
                        RuntimeMetadata metadata = runtimeMetadata(response);
                        budgets.complete(userId, "free-ask", response == null ? 0 : response.promptTokens(),
                                response == null ? 0 : response.completionTokens(), metadata.degraded());
                        emitter.send(SseEmitter.event().name("done").data(doneMetadata(metadata)));
                        emitter.complete();
                    } catch (Exception ex) {
                        emitter.completeWithError(ex);
                    }
                }

                @Override
                public void onError(Throwable cause) {
                    completeStreamWithFallback(emitter, terminal, answer, key, question, knowledgePoint, userId);
                }
            });
        } catch (Exception ex) {
            completeStreamWithFallback(emitter, terminal, answer, key, question, knowledgePoint, userId);
        }
        return emitter;
    }

    public String startDeepReview(long userId, long sessionId, String question, String answer,
                                  List<String> hits, List<String> misses, String standardAnswer) {
        String taskId = UUID.randomUUID().toString();
        DeepReviewTask pending = new DeepReviewTask(taskId, userId, sessionId, "pending", null,
                false, null, null);
        reviewTasks.put(taskId, pending);
        Optional<AuthSession> captured = AuthContext.current();
        executor.execute(() -> withAuth(captured,
                () -> runDeepReview(pending, question, answer, safeList(hits), safeList(misses), standardAnswer)));
        return taskId;
    }

    public DeepReviewTask getDeepReview(long userId, long sessionId, String taskId) {
        DeepReviewTask task = reviewTasks.get(taskId);
        if (task == null || task.userId() != userId || task.sessionId() != sessionId) {
            throw new IllegalArgumentException("AI 讲评任务不存在或无权访问");
        }
        return task;
    }

    public PersonalizedAdvice personalizedAdvice(long userId, String fingerprint, List<String> courseTitles,
                                                  int averageScore, List<String> weakTags) {
        String normalizedFingerprint = fingerprint == null ? "" : fingerprint;
        CachedAdvice cached = adviceCache.get(userId);
        if (cached != null && cached.fingerprint().equals(normalizedFingerprint)) {
            return cached.advice();
        }
        Object lock = adviceLocks.computeIfAbsent(userId, ignored -> new Object());
        synchronized (lock) {
            cached = adviceCache.get(userId);
            if (cached != null && cached.fingerprint().equals(normalizedFingerprint)) {
                return cached.advice();
            }
            PersonalizedAdvice generated = generateAdvice(userId, courseTitles, averageScore, weakTags);
            adviceCache.put(userId, new CachedAdvice(normalizedFingerprint, generated));
            return generated;
        }
    }

    public String personalizedReview(long userId, String courseTitle, int averageScore, List<String> weakTags) {
        String fingerprint = "review:" + courseTitle + ":" + averageScore + ":" + String.join("|", safeList(weakTags));
        return personalizedAdvice(userId, fingerprint, List.of(courseTitle), averageScore, weakTags).summary();
    }

    public String interviewFollowUp(long userId, String topic, String answer, List<String> expected, int depth) {
        List<AiMessage> messages = List.of(
                new AiMessage("system", "你是技术面试官。只生成一个简短追问，不超过50字；无需追问时仅返回 NO_FOLLOW_UP。"),
                new AiMessage("user", "题目：" + topic + "\n候选人回答：" + answer
                        + "\n期望关键词：" + String.join("、", safeList(expected))
                        + "\n追问层级：" + depth));
        AiTokenBudgetService.BudgetDecision budget = budgets.reserve(userId, "interview-follow-up", messages);
        if (!budget.allowed()) return "NO_FOLLOW_UP";
        try {
            AiChatResponse response = gateway.chat(request("interview-follow-up", budget.messages(), budget.maxTokens()));
            RuntimeMetadata metadata = runtimeMetadata(response);
            budgets.complete(userId, "interview-follow-up", response.promptTokens(), response.completionTokens(), metadata.degraded());
            return safeContent(response.content(), "NO_FOLLOW_UP");
        } catch (RuntimeException ex) {
            budgets.complete(userId, "interview-follow-up", 0, 0, true);
            return "NO_FOLLOW_UP";
        }
    }

    public AiTokenBudgetService.BudgetStatus budgetStatus(long userId) {
        return budgets.status(userId);
    }

    private PersonalizedAdvice generateAdvice(long userId, List<String> courseTitles, int averageScore,
                                               List<String> weakTags) {
        List<String> courses = safeList(courseTitles);
        List<String> weaknesses = safeList(weakTags);
        List<AiMessage> messages = List.of(
                new AiMessage("system", "你是小绒老师。根据学习数据给出2-3条具体、可执行、互不重复的中文建议；每行一条，不要JSON，总长度不超过180字。"),
                new AiMessage("user", "课程：" + String.join("、", courses)
                        + "\n平均分：" + averageScore
                        + "\n薄弱点：" + String.join("、", weaknesses)));
        AiTokenBudgetService.BudgetDecision budget = budgets.reserve(userId, "personal-review", messages);
        if (!budget.allowed()) return fallbackAdvice(courses, averageScore, weaknesses);
        try {
            AiChatResponse response = gateway.chat(request("personal-review", budget.messages(), budget.maxTokens()));
            RuntimeMetadata metadata = runtimeMetadata(response);
            budgets.complete(userId, "personal-review", response.promptTokens(), response.completionTokens(), metadata.degraded());
            List<String> suggestions = parseSuggestions(response.content());
            if (suggestions.size() < 2) {
                suggestions = mergeFallback(suggestions, fallbackSuggestions(courses, averageScore, weaknesses));
            }
            return new PersonalizedAdvice(suggestions, String.join("；", suggestions), metadata.degraded(),
                    metadata.providerCode(), metadata.model());
        } catch (RuntimeException ex) {
            budgets.complete(userId, "personal-review", 0, 0, true);
            return fallbackAdvice(courses, averageScore, weaknesses);
        }
    }

    private void runDeepReview(DeepReviewTask pending, String question, String answer, List<String> hits,
                               List<String> misses, String standardAnswer) {
        List<AiMessage> messages = List.of(
                new AiMessage("system", "你是小绒老师。请针对低分答案进行深度讲评：先肯定已掌握点，再解释遗漏点，最后给出可直接复述的标准表达。不超过500字。"),
                new AiMessage("user", "题目：" + question + "\n学生回答：" + answer
                        + "\n命中：" + String.join("、", hits)
                        + "\n遗漏：" + String.join("、", misses)
                        + "\n标准答案：" + standardAnswer));
        AiTokenBudgetService.BudgetDecision budget = budgets.reserve(pending.userId(), "deep-review", messages);
        if (!budget.allowed()) {
            finishFailed(pending, misses, standardAnswer);
            return;
        }
        try {
            AiChatResponse response = gateway.chat(request("deep-review", budget.messages(), budget.maxTokens()));
            RuntimeMetadata metadata = runtimeMetadata(response);
            budgets.complete(pending.userId(), "deep-review", response.promptTokens(), response.completionTokens(), metadata.degraded());
            reviewTasks.put(pending.taskId(), new DeepReviewTask(
                    pending.taskId(), pending.userId(), pending.sessionId(), "completed",
                    safeContent(response.content(), fallbackDeepReview(misses, standardAnswer)),
                    metadata.degraded(), metadata.providerCode(), metadata.model()));
        } catch (RuntimeException ex) {
            budgets.complete(pending.userId(), "deep-review", 0, 0, true);
            finishFailed(pending, misses, standardAnswer);
        }
    }

    private void finishFailed(DeepReviewTask pending, List<String> misses, String standardAnswer) {
        reviewTasks.put(pending.taskId(), new DeepReviewTask(
                pending.taskId(), pending.userId(), pending.sessionId(), "failed",
                fallbackDeepReview(misses, standardAnswer), true, null, null));
    }

    private List<AiMessage> freeAskMessages(String key, String knowledgePoint, String question) {
        ArrayList<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("system", "\u4f60\u662f\u5c0f\u7ed2\u8001\u5e08\uff1a\u6e29\u548c\u3001\u4e13\u4e1a\u3001\u50cf\u8ba4\u771f\u5907\u8bfe\u7684\u5e74\u8f7b\u52a9\u6559\u3002\u59cb\u7ec8\u4ee5\u5c0f\u7ed2\u8001\u5e08\u8eab\u4efd\u56de\u7b54\uff0c\u4e0d\u81ea\u79f0\u901a\u7528\u6a21\u578b\u6216\u52a9\u624b\u3002\u5148\u7ed9\u7ed3\u8bba\uff0c\u518d\u75281\uff5e2\u4e2a\u5173\u952e\u7406\u7531\u6216\u4e00\u4e2a\u6700\u5c0f\u4f8b\u5b50\u8bf4\u660e\uff1b\u5fc5\u8981\u65f6\u6700\u540e\u7ed9\u4e00\u4e2a\u53ef\u6267\u884c\u7684\u5c0f\u7ec3\u4e60\u3002\u8868\u8fbe\u8981\u4e0d\u8bf4\u5e9f\u8bdd\u3001\u4e0d\u5bd2\u6684\u3001\u4e0d\u91cd\u590d\u9898\u76ee\uff0c\u63a7\u5236\u57283\uff5e5\u53e5\u3001180\u5b57\u5185\uff1b\u672f\u8bed\u8981\u8865\u4e00\u53e5\u767d\u8bdd\u89e3\u91ca\u3002\u5b66\u751f\u7b54\u9519\u65f6\u5148\u80af\u5b9a\u65b9\u5411\uff0c\u518d\u6307\u51fa\u9519\u8bef\u5e76\u6362\u66f4\u7b80\u5355\u7684\u8bf4\u6cd5\uff1b\u4e0d\u8981\u8d23\u5907\u3001\u5938\u5f20\u5356\u840c\u6216\u604b\u7231\u5316\u8868\u8fbe\u3002"));
        ArrayDeque<AiMessage> history = histories.get(key);
        if (history != null) {
            synchronized (history) {
                messages.addAll(history);
            }
        }
        messages.add(new AiMessage("user", "当前知识点：" + knowledgePoint
                + "\n学生问题：" + question));
        return messages;
    }

    private void appendRound(String key, String question, String answer) {
        ArrayDeque<AiMessage> history = histories.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(new AiMessage("user", question));
            history.addLast(new AiMessage("assistant", answer));
            while (history.size() > 6) history.removeFirst();
        }
    }

    private List<String> parseSuggestions(String content) {
        if (content == null || content.isBlank()) return List.of();
        LinkedHashSet<String> parsed = new LinkedHashSet<>();
        String normalized = AiAnswerSanitizer.sanitize(content).replace("\r", "\n");
        for (String segment : normalized.split("\\n+|[；;]+")) {
            addSuggestion(parsed, segment);
            if (parsed.size() == 3) break;
        }
        if (parsed.size() < 2) {
            for (String segment : normalized.split("[。！？!?]+")) {
                addSuggestion(parsed, segment);
                if (parsed.size() == 3) break;
            }
        }
        return List.copyOf(parsed);
    }

    private void addSuggestion(LinkedHashSet<String> target, String segment) {
        String value = LIST_PREFIX.matcher(segment.trim()).replaceFirst("").trim();
        value = value.replaceFirst("^(?:建议|行动)\\s*[：:]\\s*", "").trim();
        if (!value.isBlank()) target.add(trimSuggestion(value));
    }

    private List<String> mergeFallback(List<String> parsed, List<String> fallback) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(parsed);
        for (String value : fallback) {
            merged.add(value);
            if (merged.size() == 3) break;
        }
        return List.copyOf(merged);
    }

    private PersonalizedAdvice fallbackAdvice(List<String> courseTitles, int averageScore, List<String> weakTags) {
        List<String> suggestions = fallbackSuggestions(courseTitles, averageScore, weakTags);
        return new PersonalizedAdvice(suggestions, String.join("；", suggestions), true, null, null);
    }

    private List<String> fallbackSuggestions(List<String> courseTitles, int averageScore, List<String> weakTags) {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        if (!weakTags.isEmpty()) suggestions.add("重刷“" + weakTags.get(0) + "”相关低分题");
        suggestions.add(averageScore < 70
                ? "把每道错题答案压缩成3个关键词"
                : "用自己的话复述本轮核心知识点");
        if (!courseTitles.isEmpty()) suggestions.add("完成“" + courseTitles.get(0) + "”的一次针对性练习");
        suggestions.add("选择一个薄弱点向小绒老师继续追问");
        return suggestions.stream().limit(3).toList();
    }

    private RuntimeMetadata runtimeMetadata(AiChatResponse response) {
        if (response == null) return RuntimeMetadata.degradedFallback();
        return new RuntimeMetadata(Boolean.TRUE.equals(response.mock()),
                safePublicMetadata(response.providerCode(), 64),
                safePublicMetadata(response.model(), 128));
    }

    private String safePublicMetadata(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = AiAnswerSanitizer.sanitize(value).trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (trimmed.length() > maxLength || !PUBLIC_METADATA.matcher(trimmed).matches()
                || lower.startsWith("sk-") || lower.contains("apikey") || lower.contains("api_key")
                || lower.contains("authorization") || lower.contains("bearer") || lower.contains("secret")) {
            return null;
        }
        return trimmed;
    }

    private Map<String, Object> doneMetadata(RuntimeMetadata metadata) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("degraded", metadata.degraded());
        data.put("providerCode", metadata.providerCode());
        data.put("model", metadata.model());
        return data;
    }

    private AiChatRequest request(String scene, List<AiMessage> messages, int maxTokens) {
        return new AiChatRequest(scene, null, null, messages, 0.3, maxTokens, false, null);
    }

    private AiChatRequest streamRequest(String scene, List<AiMessage> messages, int maxTokens) {
        return new AiChatRequest(scene, null, null, messages, 0.3, maxTokens, true, null);
    }

    private void completeStreamWithFallback(SseEmitter emitter, AtomicBoolean terminal, StringBuilder answer,
                                            String key, String question, String knowledgePoint, long userId) {
        if (!terminal.compareAndSet(false, true)) return;
        String fallback = fallbackAsk(knowledgePoint);
        try {
            synchronized (answer) {
                answer.setLength(0);
                answer.append(fallback);
            }
            appendRound(key, question, fallback);
            budgets.complete(userId, "free-ask", 0, 0, true);
            emitter.send(SseEmitter.event().name("delta").data(Map.of("text", fallback)));
            emitter.send(SseEmitter.event().name("done")
                    .data(doneMetadata(RuntimeMetadata.degradedFallback())));
            emitter.complete();
        } catch (Exception sendError) {
            emitter.completeWithError(sendError);
        }
    }

    private String historyKey(long userId, long sessionId, String nodeId) {
        return userId + ":" + sessionId + ":" + nodeId;
    }

    private String fallbackAsk(String knowledgePoint) {
        return "当前 AI 暂时不可用。先围绕“" + knowledgePoint
                + "”写出定义、使用场景和一个例子，我再帮你继续拆解。";
    }

    private String fallbackDeepReview(List<String> misses, String standardAnswer) {
        return "本题需要重点补齐：" + String.join("、", safeList(misses))
                + "。建议先对照标准表达复述一遍：" + standardAnswer;
    }

    private String safeContent(String content, String fallback) {
        String sanitized = AiAnswerSanitizer.sanitize(content);
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private String trimSuggestion(String value) {
        String trimmed = AiAnswerSanitizer.sanitize(value).trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    private List<String> safeList(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
    }

    private SseEmitter fallbackEmitter(String text) {
        SseEmitter emitter = new SseEmitter(30_000L);
        executor.execute(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("ready")
                        .reconnectTime(3_000L)
                        .data(Map.of("status", "connected")));
                emitter.send(SseEmitter.event().name("delta").data(Map.of("text", text)));
                emitter.send(SseEmitter.event().name("done")
                        .data(doneMetadata(RuntimeMetadata.degradedFallback())));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private void withAuth(Optional<AuthSession> session, Runnable task) {
        try {
            session.ifPresent(AuthContext::set);
            task.run();
        } finally {
            AuthContext.clear();
        }
    }

    public record ConversationAnswer(String answer, List<String> relatedKeywords, boolean degraded,
                                     String providerCode, String model) {
        public ConversationAnswer(String answer, List<String> relatedKeywords, boolean degraded) {
            this(answer, relatedKeywords, degraded, null, null);
        }
    }

    public record PersonalizedAdvice(List<String> suggestions, String summary, boolean degraded,
                                     String providerCode, String model) {}

    public record DeepReviewTask(String taskId, long userId, long sessionId, String status, String content,
                                 boolean degraded, String providerCode, String model) {
        public DeepReviewTask(String taskId, long userId, long sessionId, String status, String content,
                              boolean degraded) {
            this(taskId, userId, sessionId, status, content, degraded, null, null);
        }
    }

    private record CachedAdvice(String fingerprint, PersonalizedAdvice advice) {}

    private record RuntimeMetadata(boolean degraded, String providerCode, String model) {
        private static RuntimeMetadata degradedFallback() {
            return new RuntimeMetadata(true, null, null);
        }
    }
}
