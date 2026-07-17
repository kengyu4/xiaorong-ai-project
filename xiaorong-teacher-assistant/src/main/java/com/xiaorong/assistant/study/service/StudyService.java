package com.xiaorong.assistant.study.service;

import com.xiaorong.assistant.study.dto.StudyDtos.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface StudyService {
    List<CourseSummary> listCourses(Long subjectId);
    StudyOverviewResponse getOverview();
    default AdviceResponse getAdvice() {
        return new AdviceResponse(List.of(), "", 0, List.of(), List.of(), false, true, null, null);
    }
    SessionCreateResponse createSession(CreateSessionRequest request);
    LessonScriptResponse getScript(Long sessionId);
    NodeSubmitResponse submitNode(Long sessionId, String nodeId, SubmitAnswerRequest request);
    ClassmateSubmitResponse submitClassmate(Long sessionId, String nodeId, SubmitAnswerRequest request);
    AskResponse ask(Long sessionId, AskRequest request);
    SseEmitter askStream(Long sessionId, AskRequest request);
    AiReviewStatusResponse getAiReview(Long sessionId, String taskId);
    InterviewFollowUpResponse interviewFollowUp(Long sessionId, InterviewFollowUpRequest request);
    TokenBudgetStatusResponse getAiBudget();
    HomeworkResponse getHomework(Long sessionId);
    HomeworkSubmitResponse submitHomework(Long sessionId, Long topicId, SubmitAnswerRequest request);
    ReviewResponse getReview(Long sessionId);
}