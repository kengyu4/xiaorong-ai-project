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
import com.xiaorong.assistant.study.content.StudyMaterial;
import com.xiaorong.assistant.study.dto.StudyDtos.AdviceResponse;
import com.xiaorong.assistant.study.dto.StudyDtos.ClassmateView;
import com.xiaorong.assistant.study.dto.StudyDtos.CourseSummary;
import com.xiaorong.assistant.study.dto.StudyDtos.PersonaView;
import com.xiaorong.assistant.study.dto.StudyDtos.ReviewResponse;
import com.xiaorong.assistant.study.persistence.StudyJdbcRepository;
import com.xiaorong.assistant.study.persistence.StudyJdbcRepository.RecordRow;
import com.xiaorong.assistant.study.persistence.StudyJdbcRepository.SessionRow;
import com.xiaorong.assistant.study.persistence.StudyMaterialCache;
import com.xiaorong.assistant.study.service.StudyScoringService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlRedisStudyServiceTest {
    private StudyJdbcRepository repository;
    private StudyMaterialCache cache;

    @BeforeEach
    void setUp() {
        repository = mock(StudyJdbcRepository.class);
        cache = mock(StudyMaterialCache.class);
        AuthContext.set(new AuthSession(9L, "student", "Student", List.of("USER")));
    }

    @AfterEach
    void tearDown() { AuthContext.clear(); }

    @Test
    void overviewAdviceUsesCurrentUsersFullLearningDataAndCachesByDataFingerprint() {
        RecordingGateway gateway = new RecordingGateway(List.of(
                "1. 重刷 Proxy 低分题\n2. 复述 toRefs 使用边界\n3. 完成 Vue 课程巩固题",
                "1. 复习 Reflect\n2. 完成更新后的课程练习"
        ));
        MysqlRedisStudyService service = service(gateway);
        List<RecordRow> firstRecords = List.of(
                record("lesson", 60, List.of("Proxy"), 1),
                record("homework", 80, List.of("toRefs"), 2),
                record("free_ask", 0, List.of(), 3)
        );
        List<RecordRow> changedRecords = List.of(
                record("lesson", 60, List.of("Proxy"), 1),
                record("homework", 80, List.of("toRefs"), 2),
                record("lesson", 90, List.of("Reflect"), 4)
        );
        when(repository.listRecordsByUser(9L)).thenReturn(firstRecords, firstRecords, changedRecords);
        when(repository.listCourses()).thenReturn(List.of(course(101L, "Vue 响应式"), course(102L, "Spring 基础")));

        AdviceResponse first = service.getAdvice();
        AdviceResponse cached = service.getAdvice();
        AdviceResponse changed = service.getAdvice();

        assertThat(first.averageScore()).isEqualTo(70);
        assertThat(first.weakTags()).containsExactly("Proxy", "toRefs");
        assertThat(first.courseTitles()).containsExactly("Vue 响应式", "Spring 基础");
        assertThat(first.suggestions()).hasSize(3);
        assertThat(first.providerCode()).isEqualTo("deepseek");
        assertThat(first.model()).isEqualTo("deepseek-chat");
        assertThat(first.degraded()).isFalse();
        assertThat(cached.suggestions()).isEqualTo(first.suggestions());
        assertThat(changed.suggestions()).containsExactly("复习 Reflect", "完成更新后的课程练习");
        assertThat(gateway.requests).hasSize(2);
        verify(repository, org.mockito.Mockito.times(3)).listRecordsByUser(9L);
    }

    @Test
    void sessionReviewUsesParsedAiSuggestionsAsNextActions() {
        RecordingGateway gateway = new RecordingGateway(List.of(
                "1. 重刷 Proxy 低分题\n2. 用三句话解释 toRefs\n3. 完成 Vue 响应式复盘"
        ));
        MysqlRedisStudyService service = service(gateway);
        when(repository.getSession(501L)).thenReturn(new SessionRow(
                501L, 9L, 101L, "learning", 0, 0, 0, 6, LocalDateTime.of(2026, 7, 16, 9, 0)));
        when(repository.listRecords(501L)).thenReturn(List.of(
                record("lesson", 60, List.of("Proxy"), 1),
                record("homework", 80, List.of("toRefs"), 2)
        ));
        when(cache.get(101L)).thenReturn(Optional.of(material()));

        ReviewResponse review = service.getReview(501L);

        assertThat(review.nextActions()).containsExactly(
                "重刷 Proxy 低分题", "用三句话解释 toRefs", "完成 Vue 响应式复盘");
        assertThat(review.teacherSummary()).contains("重刷 Proxy 低分题");
        assertThat(review.nextActions()).doesNotContain(
                "重刷低于 70 分的题", "把每题答案压缩成 3 个关键词", "用白子请教题做二次讲解");
    }

    private MysqlRedisStudyService service(RecordingGateway gateway) {
        StudyAiConversationService conversation = new StudyAiConversationService(
                gateway, new AiTokenBudgetService(), Runnable::run);
        return new MysqlRedisStudyService(repository, cache, new StudyScoringService(), conversation,
                new StudyOverviewAggregator(), new InterviewFollowUpPolicy());
    }

    private RecordRow record(String type, int score, List<String> misses, int minute) {
        return new RecordRow("node-" + minute, type, null, score, List.of(), misses, "feedback", 0,
                LocalDateTime.of(2026, 7, 16, 9, minute));
    }

    private CourseSummary course(long id, String title) {
        return new CourseSummary(id, title, title + " 描述", "中等", List.of("tag"), 1, 1,
                "/teacher.png", "/classmate.png");
    }

    private StudyMaterial material() {
        return new StudyMaterial(course(101L, "Vue 响应式"),
                new PersonaView("小绒老师", "/teacher.png", "teacher"),
                new ClassmateView("白子", "/classmate.png", 0), List.of(), List.of());
    }

    private static final class RecordingGateway implements AiGatewayService {
        private final List<AiChatRequest> requests = new ArrayList<>();
        private final List<String> contents;

        private RecordingGateway(List<String> contents) { this.contents = contents; }

        @Override
        public AiChatResponse chat(AiChatRequest request) {
            requests.add(request);
            String content = contents.get(Math.min(requests.size() - 1, contents.size() - 1));
            return new AiChatResponse("deepseek", "deepseek-chat", request.scene(), content, 20, 30, 2L, false);
        }

        @Override
        public AiStructuredResponse structured(AiStructuredRequest request) { throw new UnsupportedOperationException(); }

        @Override
        public SseEmitter stream(AiChatRequest request) { throw new UnsupportedOperationException(); }
    }
}
