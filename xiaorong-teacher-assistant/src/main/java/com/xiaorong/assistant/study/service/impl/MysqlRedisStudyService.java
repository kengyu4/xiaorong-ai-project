package com.xiaorong.assistant.study.service.impl;

import com.xiaorong.assistant.auth.AuthContext;
import com.xiaorong.assistant.auth.exception.ForbiddenException;
import com.xiaorong.assistant.study.ai.AiTokenBudgetService;
import com.xiaorong.assistant.study.ai.InterviewFollowUpPolicy;
import com.xiaorong.assistant.study.ai.StudyAiConversationService;
import com.xiaorong.assistant.study.ai.StudyOverviewAggregator;
import com.xiaorong.assistant.study.content.StudyMaterial;
import com.xiaorong.assistant.study.content.StudyMaterial.HomeworkSeed;
import com.xiaorong.assistant.study.dto.StudyDtos.*;
import com.xiaorong.assistant.study.persistence.StudyJdbcRepository;
import com.xiaorong.assistant.study.persistence.StudyJdbcRepository.RecordRow;
import com.xiaorong.assistant.study.persistence.StudyJdbcRepository.SessionRow;
import com.xiaorong.assistant.study.persistence.StudyMaterialCache;
import com.xiaorong.assistant.study.service.StudyScoringService;
import com.xiaorong.assistant.study.service.StudyScoringService.ScoreResult;
import com.xiaorong.assistant.study.service.StudyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "xiaorong.persistence", name = "enabled", havingValue = "true")
public class MysqlRedisStudyService implements StudyService {
    private final StudyJdbcRepository repository;
    private final StudyMaterialCache cache;
    private final StudyScoringService scoringService;
    private final StudyAiConversationService aiConversation;
    private final StudyOverviewAggregator overviewAggregator;
    private final InterviewFollowUpPolicy followUpPolicy;

    public MysqlRedisStudyService(StudyJdbcRepository repository, StudyMaterialCache cache,
                                  StudyScoringService scoringService, StudyAiConversationService aiConversation,
                                  StudyOverviewAggregator overviewAggregator, InterviewFollowUpPolicy followUpPolicy) {
        this.repository = repository; this.cache = cache; this.scoringService = scoringService;
        this.aiConversation = aiConversation; this.overviewAggregator = overviewAggregator; this.followUpPolicy = followUpPolicy;
    }

    @Override public List<CourseSummary> listCourses(Long subjectId) { return repository.listCourses(); }

    @Override
    public StudyOverviewResponse getOverview() {
        long userId = AuthContext.requireUserId();
        List<RecordRow> records = repository.listRecordsByUser(userId);
        StudyOverviewAggregator.Overview overview = overviewAggregator.aggregate(
                records.stream().map(RecordRow::missKeywords).toList(), repository.countRecordsByUser(userId));
        return new StudyOverviewResponse(overview.topWeakTag(), overview.weakTags(), overview.weakTagCount(), overview.completedCount());
    }

    @Override
    public AdviceResponse getAdvice() {
        long userId = AuthContext.requireUserId();
        List<RecordRow> records = repository.listRecordsByUser(userId);
        List<RecordRow> scoredRecords = records.stream()
                .filter(record -> !"free_ask".equals(record.nodeType()))
                .toList();
        int averageScore = scoredRecords.isEmpty() ? 0 : (int) Math.round(
                scoredRecords.stream().mapToInt(RecordRow::score).average().orElse(0));
        LinkedHashSet<String> weakTagSet = scoredRecords.stream()
                .flatMap(record -> record.missKeywords().stream())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> weakTags = weakTagSet.stream().limit(5).toList();
        List<String> courseTitles = repository.listCourses().stream()
                .map(CourseSummary::title)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        String fingerprint = "overview:" + records.stream()
                .map(record -> String.valueOf(record.nodeId()) + ':' + record.nodeType() + ':' + record.score()
                        + ':' + record.missKeywords() + ':' + record.createTime())
                .collect(Collectors.joining("|"));
        StudyAiConversationService.PersonalizedAdvice advice = aiConversation.personalizedAdvice(
                userId, fingerprint, courseTitles, averageScore, weakTags);
        return new AdviceResponse(advice.suggestions(), advice.summary(), averageScore, weakTags, courseTitles,
                !scoredRecords.isEmpty(), advice.degraded(), advice.providerCode(), advice.model());
    }

    @Override
    public SessionCreateResponse createSession(CreateSessionRequest request) {
        StudyMaterial material = material(request.courseId());
        Long sessionId = repository.createSession(AuthContext.requireUserId(), material.course().courseId(), request.mode());
        return new SessionCreateResponse(sessionId, material.course().courseId(), "learning", 0);
    }

    @Override
    public LessonScriptResponse getScript(Long sessionId) {
        SessionRow session = session(sessionId); StudyMaterial material = material(session.courseId());
        return new LessonScriptResponse(material.course().courseId(), material.course().title(), material.teacher(),
                new ClassmateView(material.classmate().name(), material.classmate().avatar(), session.bondValue()), material.nodes());
    }

    @Override
    public NodeSubmitResponse submitNode(Long sessionId, String nodeId, SubmitAnswerRequest request) {
        SessionRow session = session(sessionId); StudyMaterial material = material(session.courseId());
        LessonNode node = findNode(material, nodeId); ScoreResult result = scoringService.score(request.answerText(), node.answerKeywords());
        int nextNodeIndex = nextNodeIndex(material, nodeId);
        String teacherReply = result.score() >= 70 ? "这个回答方向是对的。你已经抓住了核心点，我帮你补一句更适合面试的表达。" : "这里容易混淆。我们先退一步，只看这个概念最关键的边界。";
        String feedback = result.score() >= 70 ? "方向对了，面试时可以再补一句：" + safeText(node.explanation()) : "先把这些关键词找回来：" + String.join("、", result.missKeywords());
        repository.insertRecord(sessionId, session.userId(), session.courseId(), nodeId, node.type(), null,
                request.answerText(), result.score(), result.hitKeywords(), result.missKeywords(), feedback, 0);
        repository.updateCurrentNode(sessionId, nextNodeIndex);
        boolean needAiReview = result.score() < 70;
        String taskId = needAiReview ? aiConversation.startDeepReview(session.userId(), sessionId, safeText(node.question()),
                request.answerText(), result.hitKeywords(), result.missKeywords(), safeText(node.standardAnswer())) : null;
        return new NodeSubmitResponse(result.score(), result.hitKeywords(), result.missKeywords(), feedback,
                teacherReply, nextNodeIndex, needAiReview, taskId);
    }

    @Override
    public ClassmateSubmitResponse submitClassmate(Long sessionId, String nodeId, SubmitAnswerRequest request) {
        SessionRow session = session(sessionId); LessonNode node = findNode(material(session.courseId()), nodeId);
        ScoreResult result = scoringService.score(request.answerText(), node.answerKeywords()); int bondDelta = result.score() >= 70 ? 3 : 1;
        String feedback = result.score() >= 70 ? "白子听懂了你的解释。" : "白子也卡在这里，你们可以一起把关键词补回来。";
        repository.insertRecord(sessionId, session.userId(), session.courseId(), nodeId, node.type(), null,
                request.answerText(), result.score(), result.hitKeywords(), result.missKeywords(), feedback, bondDelta);
        repository.addBond(sessionId, bondDelta);
        String reply = result.score() >= 70 ? "谢谢你，这样一讲我就清楚多了。讲清楚了 +" + bondDelta : "没关系，我也卡在这里。一起复盘 +" + bondDelta;
        return new ClassmateSubmitResponse(result.score(), bondDelta, session.bondValue() + bondDelta,
                result.hitKeywords(), result.missKeywords(), reply, safeText(node.explanation()));
    }

    @Override
    public AskResponse ask(Long sessionId, AskRequest request) {
        SessionRow session = session(sessionId); LessonNode node = findNode(material(session.courseId()), request.nodeId());
        StudyAiConversationService.ConversationAnswer answer = aiConversation.ask(session.userId(), sessionId, request.nodeId(),
                node.knowledgePoint(), request.question(), node.answerKeywords());
        repository.insertRecord(sessionId, session.userId(), session.courseId(), request.nodeId(), "free_ask", null,
                request.question(), 0, List.of(), List.of(), answer.answer(), 0);
        return new AskResponse(answer.answer(), answer.relatedKeywords(), answer.degraded(),
                answer.providerCode(), answer.model());
    }

    @Override
    public SseEmitter askStream(Long sessionId, AskRequest request) {
        SessionRow session = session(sessionId); LessonNode node = findNode(material(session.courseId()), request.nodeId());
        return aiConversation.streamAsk(session.userId(), sessionId, request.nodeId(), node.knowledgePoint(), request.question());
    }

    @Override
    public AiReviewStatusResponse getAiReview(Long sessionId, String taskId) {
        SessionRow session = session(sessionId);
        StudyAiConversationService.DeepReviewTask task = aiConversation.getDeepReview(session.userId(), sessionId, taskId);
        return new AiReviewStatusResponse(task.taskId(), task.status(), task.content(), task.degraded(),
                task.providerCode(), task.model());
    }

    @Override
    public InterviewFollowUpResponse interviewFollowUp(Long sessionId, InterviewFollowUpRequest request) {
        SessionRow session = session(sessionId); LessonNode node = findNode(material(session.courseId()), request.nodeId());
        int level = request.followUpLevel() == null ? 0 : request.followUpLevel();
        InterviewFollowUpPolicy.Decision decision = followUpPolicy.decide(request.answerText(), node.answerKeywords(), level);
        String question = decision.mode().equals("ai") ? aiConversation.interviewFollowUp(session.userId(), node.question(), request.answerText(), node.answerKeywords(), level) : decision.fixedQuestion();
        return new InterviewFollowUpResponse(decision.mode(), "NO_FOLLOW_UP".equals(question) ? null : question,
                Math.min(2, level + (decision.mode().equals("none") ? 0 : 1)));
    }

    @Override public TokenBudgetStatusResponse getAiBudget() { return budgetResponse(aiConversation.budgetStatus(AuthContext.requireUserId())); }

    @Override
    public HomeworkResponse getHomework(Long sessionId) {
        SessionRow session = session(sessionId);
        return new HomeworkResponse(material(session.courseId()).homework().stream()
                .map(item -> new HomeworkItem(item.topicId(), item.title(), item.body(), item.tags(), item.difficulty())).toList());
    }

    @Override
    public HomeworkSubmitResponse submitHomework(Long sessionId, Long topicId, SubmitAnswerRequest request) {
        SessionRow session = session(sessionId); HomeworkSeed item = findHomework(material(session.courseId()), topicId);
        ScoreResult result = scoringService.score(request.answerText(), item.keywords());
        String feedback = result.score() >= 70 ? "答案已经比较完整，建议补上遗漏关键词：" + String.join("、", result.missKeywords()) : "这题先别急着背答案，先把概念定义、实际问题、面试表达三块补齐。";
        repository.insertRecord(sessionId, session.userId(), session.courseId(), null, "homework", topicId,
                request.answerText(), result.score(), result.hitKeywords(), result.missKeywords(), feedback, 0);
        boolean needAiReview = result.score() < 70;
        String taskId = needAiReview ? aiConversation.startDeepReview(session.userId(), sessionId, item.body(), request.answerText(),
                result.hitKeywords(), result.missKeywords(), item.standardAnswer()) : null;
        return new HomeworkSubmitResponse(result.score(), result.hitKeywords(), result.missKeywords(), feedback,
                "小绒老师讲评：" + item.standardAnswer(), item.standardAnswer(), needAiReview, taskId);
    }

    @Override
    public ReviewResponse getReview(Long sessionId) {
        SessionRow session = session(sessionId);
        List<RecordRow> records = repository.listRecords(sessionId);
        List<RecordRow> scoredRecords = records.stream()
                .filter(record -> !"free_ask".equals(record.nodeType()))
                .toList();
        int averageScore = scoredRecords.isEmpty() ? 0 : (int) Math.round(
                scoredRecords.stream().mapToInt(RecordRow::score).average().orElse(0));
        LinkedHashSet<String> weakTagSet = scoredRecords.stream()
                .flatMap(record -> record.missKeywords().stream())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> weakTags = weakTagSet.stream().limit(5).toList();
        if (weakTags.isEmpty()) weakTags = List.of("面试表达", "关键词组织");
        String courseTitle = material(session.courseId()).course().title();
        String fingerprint = "session:" + sessionId + ':' + averageScore + ':' + String.join("|", weakTags)
                + ':' + records.stream().map(record -> String.valueOf(record.createTime()))
                .collect(Collectors.joining("|"));
        StudyAiConversationService.PersonalizedAdvice advice = aiConversation.personalizedAdvice(
                session.userId(), fingerprint, List.of(courseTitle), averageScore, weakTags);
        return new ReviewResponse(averageScore, session.bondValue(), weakTags,
                "本轮学习记录已落库，下一步建议重刷薄弱关键词。",
                advice.summary(), "我也把这些关键词记下来了，我们下次一起再复习一遍。",
                advice.suggestions(), new NextCourse(session.courseId(), courseTitle),
                advice.degraded(), advice.providerCode(), advice.model());
    }

    private StudyMaterial material(Long courseId) { return cache.get(courseId).orElseGet(() -> { StudyMaterial m=repository.getMaterial(courseId); cache.put(courseId,m); return m; }); }
    private SessionRow session(Long sessionId) { SessionRow s=repository.getSession(sessionId); if (!s.userId().equals(AuthContext.requireUserId())) throw new ForbiddenException("无权访问这个学习会话"); return s; }
    private LessonNode findNode(StudyMaterial material,String nodeId){return material.nodes().stream().filter(n->n.nodeId().equals(nodeId)).findFirst().orElseThrow(()->new IllegalArgumentException("课程节点不存在"));}
    private HomeworkSeed findHomework(StudyMaterial material,Long topicId){return material.homework().stream().filter(i->i.topicId().equals(topicId)).findFirst().orElseThrow(()->new IllegalArgumentException("作业题不存在"));}
    private int nextNodeIndex(StudyMaterial material,String nodeId){for(int i=0;i<material.nodes().size();i++)if(material.nodes().get(i).nodeId().equals(nodeId))return Math.min(i+1,material.nodes().size()-1);return 0;}
    private String safeText(String text){return text==null||text.isBlank()?"暂无补充。":text;}
    private TokenBudgetStatusResponse budgetResponse(AiTokenBudgetService.BudgetStatus s){return new TokenBudgetStatusResponse(s.date(),s.requests(),s.promptTokens(),s.completionTokens(),s.totalTokens(),s.degradedRequests(),s.warning(),s.exhausted(),s.warningLimit(),s.hardLimit());}
}