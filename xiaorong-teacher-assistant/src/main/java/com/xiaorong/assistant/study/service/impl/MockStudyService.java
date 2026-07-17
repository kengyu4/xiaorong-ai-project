package com.xiaorong.assistant.study.service.impl;

import com.xiaorong.assistant.auth.AuthContext;
import com.xiaorong.assistant.auth.exception.ForbiddenException;
import com.xiaorong.assistant.study.ai.AiTokenBudgetService;
import com.xiaorong.assistant.study.ai.InterviewFollowUpPolicy;
import com.xiaorong.assistant.study.ai.StudyAiConversationService;
import com.xiaorong.assistant.study.ai.StudyOverviewAggregator;
import com.xiaorong.assistant.study.content.StudyMaterial;
import com.xiaorong.assistant.study.content.StudyMaterial.HomeworkSeed;
import com.xiaorong.assistant.study.content.StudyTemplateProvider;
import com.xiaorong.assistant.study.dto.StudyDtos.*;
import com.xiaorong.assistant.study.service.StudyScoringService;
import com.xiaorong.assistant.study.service.StudyScoringService.ScoreResult;
import com.xiaorong.assistant.study.service.StudyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "xiaorong.persistence", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MockStudyService implements StudyService {
    private final AtomicLong sessionIdGenerator = new AtomicLong(10001);
    private final Map<Long, StudySessionState> sessions = new ConcurrentHashMap<>();
    private final Map<Long, StudyMaterial> materials;
    private final StudyScoringService scoringService;
    private final StudyAiConversationService aiConversation;
    private final StudyOverviewAggregator overviewAggregator;
    private final InterviewFollowUpPolicy followUpPolicy;

    public MockStudyService(StudyTemplateProvider templateProvider, StudyScoringService scoringService,
                            StudyAiConversationService aiConversation, StudyOverviewAggregator overviewAggregator,
                            InterviewFollowUpPolicy followUpPolicy) {
        this.materials = templateProvider.loadMaterials().stream()
                .collect(Collectors.toMap(material -> material.course().courseId(), Function.identity()));
        this.scoringService = scoringService;
        this.aiConversation = aiConversation;
        this.overviewAggregator = overviewAggregator;
        this.followUpPolicy = followUpPolicy;
    }

    @Override
    public List<CourseSummary> listCourses(Long subjectId) {
        return materials.values().stream().map(StudyMaterial::course)
                .sorted(Comparator.comparing(CourseSummary::courseId)).toList();
    }

    @Override
    public StudyOverviewResponse getOverview() {
        long userId = AuthContext.requireUserId();
        List<StudySessionState> owned = sessions.values().stream().filter(s -> s.userId == userId).toList();
        StudyOverviewAggregator.Overview overview = overviewAggregator.aggregate(
                owned.stream().flatMap(s -> s.records.stream()).map(r -> r.missKeywords).toList(),
                owned.stream().mapToInt(s -> s.records.size()).sum());
        return new StudyOverviewResponse(overview.topWeakTag(), overview.weakTags(), overview.weakTagCount(), overview.completedCount());
    }

    @Override
    public AdviceResponse getAdvice() {
        long userId = AuthContext.requireUserId();
        List<StudySessionState> owned = sessions.values().stream()
                .filter(session -> session.userId == userId)
                .sorted(Comparator.comparingLong(session -> session.sessionId))
                .toList();
        List<StudyRecord> scoredRecords = owned.stream()
                .flatMap(session -> session.records.stream())
                .filter(record -> record.scored)
                .toList();
        int averageScore = scoredRecords.isEmpty() ? 0 : (int) Math.round(
                scoredRecords.stream().mapToInt(record -> record.score).average().orElse(0));
        LinkedHashSet<String> weakTagSet = scoredRecords.stream()
                .flatMap(record -> record.missKeywords.stream())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> weakTags = weakTagSet.stream().limit(5).toList();
        List<String> courseTitles = materials.values().stream()
                .map(material -> material.course().title())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        String fingerprint = "mock-overview:" + owned.stream()
                .map(session -> session.sessionId + ":" + session.records.stream()
                        .map(record -> record.score + ":" + record.scored + ":" + record.missKeywords)
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining("|"));
        StudyAiConversationService.PersonalizedAdvice advice = aiConversation.personalizedAdvice(
                userId, fingerprint, courseTitles, averageScore, weakTags);
        return new AdviceResponse(advice.suggestions(), advice.summary(), averageScore, weakTags, courseTitles,
                !scoredRecords.isEmpty(), advice.degraded(), advice.providerCode(), advice.model());
    }

    @Override
    public SessionCreateResponse createSession(CreateSessionRequest request) {
        StudyMaterial material = material(request.courseId());
        long userId = AuthContext.requireUserId();
        Long sessionId = sessionIdGenerator.getAndIncrement();
        sessions.put(sessionId, new StudySessionState(sessionId, userId, material.course().courseId(), request.mode(), LocalDateTime.now()));
        return new SessionCreateResponse(sessionId, material.course().courseId(), "learning", 0);
    }

    @Override
    public LessonScriptResponse getScript(Long sessionId) {
        StudySessionState state = state(sessionId);
        StudyMaterial material = material(state.courseId);
        return new LessonScriptResponse(material.course().courseId(), material.course().title(), material.teacher(),
                new ClassmateView(material.classmate().name(), material.classmate().avatar(), state.bondValue), material.nodes());
    }

    @Override
    public NodeSubmitResponse submitNode(Long sessionId, String nodeId, SubmitAnswerRequest request) {
        StudySessionState state = state(sessionId);
        StudyMaterial material = material(state.courseId);
        LessonNode node = findNode(material, nodeId);
        ScoreResult result = scoringService.score(request.answerText(), node.answerKeywords());
        state.currentNodeIndex = nextNodeIndex(material, nodeId);
        state.records.add(StudyRecord.checkpoint(nodeId, result.score(), result.hitKeywords(), result.missKeywords()));
        String teacherReply = result.score() >= 70
                ? "这个回答方向是对的。你已经抓住了核心点，我帮你补一句更适合面试的表达。"
                : "这里容易混淆。我们先退一步，只看这个概念最关键的边界。";
        String feedback = result.score() >= 70
                ? "方向对了，面试时可以再补一句：" + safeText(node.explanation())
                : "先把这些关键词找回来：" + String.join("、", result.missKeywords());
        boolean needAiReview = result.score() < 70;
        String taskId = needAiReview ? aiConversation.startDeepReview(state.userId, sessionId, safeText(node.question()),
                request.answerText(), result.hitKeywords(), result.missKeywords(), safeText(node.standardAnswer())) : null;
        return new NodeSubmitResponse(result.score(), result.hitKeywords(), result.missKeywords(), feedback,
                teacherReply, state.currentNodeIndex, needAiReview, taskId);
    }

    @Override
    public ClassmateSubmitResponse submitClassmate(Long sessionId, String nodeId, SubmitAnswerRequest request) {
        StudySessionState state = state(sessionId);
        LessonNode node = findNode(material(state.courseId), nodeId);
        ScoreResult result = scoringService.score(request.answerText(), node.answerKeywords());
        int bondDelta = result.score() >= 70 ? 3 : 1;
        state.bondValue += bondDelta;
        state.records.add(StudyRecord.classmate(nodeId, result.score(), result.hitKeywords(), result.missKeywords(), bondDelta));
        String reply = result.score() >= 70 ? "谢谢你，这样一讲我就清楚多了。讲清楚了 +" + bondDelta
                : "没关系，我也卡在这里。我们一起把老师刚才说的关键词找回来。一起复盘 +" + bondDelta;
        return new ClassmateSubmitResponse(result.score(), bondDelta, state.bondValue, result.hitKeywords(),
                result.missKeywords(), reply, safeText(node.explanation()));
    }

    @Override
    public AskResponse ask(Long sessionId, AskRequest request) {
        StudySessionState state = state(sessionId);
        LessonNode node = findNode(material(state.courseId), request.nodeId());
        StudyAiConversationService.ConversationAnswer answer = aiConversation.ask(state.userId, sessionId, request.nodeId(),
                node.knowledgePoint(), request.question(), node.answerKeywords());
        state.records.add(StudyRecord.freeAsk(request.nodeId()));
        return new AskResponse(answer.answer(), answer.relatedKeywords(), answer.degraded(),
                answer.providerCode(), answer.model());
    }

    @Override
    public SseEmitter askStream(Long sessionId, AskRequest request) {
        StudySessionState state = state(sessionId);
        LessonNode node = findNode(material(state.courseId), request.nodeId());
        return aiConversation.streamAsk(state.userId, sessionId, request.nodeId(), node.knowledgePoint(), request.question());
    }

    @Override
    public AiReviewStatusResponse getAiReview(Long sessionId, String taskId) {
        StudySessionState state = state(sessionId);
        StudyAiConversationService.DeepReviewTask task = aiConversation.getDeepReview(state.userId, sessionId, taskId);
        return new AiReviewStatusResponse(task.taskId(), task.status(), task.content(), task.degraded(),
                task.providerCode(), task.model());
    }

    @Override
    public InterviewFollowUpResponse interviewFollowUp(Long sessionId, InterviewFollowUpRequest request) {
        StudySessionState state = state(sessionId);
        LessonNode node = findNode(material(state.courseId), request.nodeId());
        int level = request.followUpLevel() == null ? 0 : request.followUpLevel();
        InterviewFollowUpPolicy.Decision decision = followUpPolicy.decide(request.answerText(), node.answerKeywords(), level);
        String question = decision.mode().equals("ai")
                ? aiConversation.interviewFollowUp(state.userId, node.question(), request.answerText(), node.answerKeywords(), level)
                : decision.fixedQuestion();
        return new InterviewFollowUpResponse(decision.mode(), "NO_FOLLOW_UP".equals(question) ? null : question,
                Math.min(2, level + (decision.mode().equals("none") ? 0 : 1)));
    }

    @Override
    public TokenBudgetStatusResponse getAiBudget() { return budgetResponse(aiConversation.budgetStatus(AuthContext.requireUserId())); }

    @Override
    public HomeworkResponse getHomework(Long sessionId) {
        StudySessionState state = state(sessionId);
        return new HomeworkResponse(material(state.courseId).homework().stream()
                .map(item -> new HomeworkItem(item.topicId(), item.title(), item.body(), item.tags(), item.difficulty())).toList());
    }

    @Override
    public HomeworkSubmitResponse submitHomework(Long sessionId, Long topicId, SubmitAnswerRequest request) {
        StudySessionState state = state(sessionId);
        HomeworkSeed item = findHomework(material(state.courseId), topicId);
        ScoreResult result = scoringService.score(request.answerText(), item.keywords());
        state.records.add(StudyRecord.homework(String.valueOf(topicId), result.score(), result.hitKeywords(), result.missKeywords()));
        String feedback = result.score() >= 70 ? "答案已经比较完整，建议补上遗漏关键词：" + String.join("、", result.missKeywords())
                : "这题先别急着背答案，先把概念定义、实际问题、面试表达三块补齐。";
        boolean needAiReview = result.score() < 70;
        String taskId = needAiReview ? aiConversation.startDeepReview(state.userId, sessionId, item.body(), request.answerText(),
                result.hitKeywords(), result.missKeywords(), item.standardAnswer()) : null;
        return new HomeworkSubmitResponse(result.score(), result.hitKeywords(), result.missKeywords(), feedback,
                "小绒老师讲评：" + item.standardAnswer(), item.standardAnswer(), needAiReview, taskId);
    }

    @Override
    public ReviewResponse getReview(Long sessionId) {
        StudySessionState state = state(sessionId);
        List<StudyRecord> scoredRecords = state.records.stream().filter(record -> record.scored).toList();
        int averageScore = scoredRecords.isEmpty() ? 0 : (int) Math.round(
                scoredRecords.stream().mapToInt(record -> record.score).average().orElse(0));
        Set<String> weakTagSet = scoredRecords.stream()
                .flatMap(record -> record.missKeywords.stream())
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        List<String> weakTags = weakTagSet.stream().limit(5).toList();
        if (weakTags.isEmpty()) weakTags = List.of("\u9762\u8bd5\u8868\u8fbe", "\u5173\u952e\u8bcd\u7ec4\u7ec7");
        String courseTitle = material(state.courseId).course().title();
        String fingerprint = "mock-session:" + sessionId + ':' + averageScore + ':' + String.join("|", weakTags)
                + ':' + state.records.stream()
                .map(record -> record.score + ":" + record.scored + ":" + record.missKeywords)
                .collect(Collectors.joining("|"));
        StudyAiConversationService.PersonalizedAdvice advice = aiConversation.personalizedAdvice(
                state.userId, fingerprint, List.of(courseTitle), averageScore, weakTags);
        return new ReviewResponse(averageScore, state.bondValue, weakTags,
                "\u672c\u8f6e\u5b66\u4e60\u8bb0\u5f55\u5df2\u843d\u5e93\uff0c\u4e0b\u4e00\u6b65\u5efa\u8bae\u91cd\u5237\u8584\u5f31\u5173\u952e\u8bcd\u3002", advice.summary(),
                "\u6211\u4e5f\u628a\u8fd9\u4e9b\u5173\u952e\u8bcd\u8bb0\u4e0b\u6765\u4e86\uff0c\u6211\u4eec\u4e0b\u6b21\u4e00\u8d77\u518d\u590d\u4e60\u4e00\u904d\u3002",
                advice.suggestions(), new NextCourse(state.courseId, courseTitle),
                advice.degraded(), advice.providerCode(), advice.model());
    }

    private StudySessionState state(Long sessionId) {
        StudySessionState state = sessions.get(sessionId);
        if (state == null) throw new IllegalArgumentException("学习会话不存在，请先创建 session");
        if (state.userId != AuthContext.requireUserId()) throw new ForbiddenException("无权访问这个学习会话");
        return state;
    }
    private StudyMaterial material(Long courseId) { StudyMaterial m = materials.get(courseId); if (m == null) throw new IllegalArgumentException("课程不存在"); return m; }
    private LessonNode findNode(StudyMaterial material, String nodeId) { return material.nodes().stream().filter(n -> n.nodeId().equals(nodeId)).findFirst().orElseThrow(() -> new IllegalArgumentException("课程节点不存在")); }
    private HomeworkSeed findHomework(StudyMaterial material, Long topicId) { return material.homework().stream().filter(i -> i.topicId().equals(topicId)).findFirst().orElseThrow(() -> new IllegalArgumentException("作业题不存在")); }
    private int nextNodeIndex(StudyMaterial material, String nodeId) { for (int i=0;i<material.nodes().size();i++) if (material.nodes().get(i).nodeId().equals(nodeId)) return Math.min(i+1, material.nodes().size()-1); return 0; }
    private String safeText(String text) { return text == null || text.isBlank() ? "暂无补充。" : text; }
    private TokenBudgetStatusResponse budgetResponse(AiTokenBudgetService.BudgetStatus s) { return new TokenBudgetStatusResponse(s.date(), s.requests(), s.promptTokens(), s.completionTokens(), s.totalTokens(), s.degradedRequests(), s.warning(), s.exhausted(), s.warningLimit(), s.hardLimit()); }

    private static class StudySessionState {
        final long sessionId; final long userId; final long courseId; final String mode; final LocalDateTime startedAt;
        final List<StudyRecord> records = new ArrayList<>(); int currentNodeIndex; int bondValue;
        StudySessionState(long sessionId, long userId, long courseId, String mode, LocalDateTime startedAt) { this.sessionId=sessionId; this.userId=userId; this.courseId=courseId; this.mode=mode==null||mode.isBlank()?"quick":mode; this.startedAt=startedAt; }
    }
    private static class StudyRecord {
        final int score; final List<String> missKeywords; final boolean scored;
        StudyRecord(int score, List<String> misses, boolean scored) {
            this.score = score;
            this.missKeywords = misses == null ? List.of() : misses;
            this.scored = scored;
        }
        static StudyRecord checkpoint(String id,int score,List<String> h,List<String> m){return new StudyRecord(score,m,true);} static StudyRecord classmate(String id,int score,List<String> h,List<String> m,int b){return new StudyRecord(score,m,true);} static StudyRecord homework(String id,int score,List<String> h,List<String> m){return new StudyRecord(score,m,true);} static StudyRecord freeAsk(String id){return new StudyRecord(0,List.of(),false);}
    }
}
