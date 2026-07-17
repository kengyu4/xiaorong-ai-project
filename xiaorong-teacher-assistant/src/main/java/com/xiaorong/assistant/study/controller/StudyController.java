package com.xiaorong.assistant.study.controller;

import com.xiaorong.assistant.common.Result;
import com.xiaorong.assistant.study.dto.StudyDtos.*;
import com.xiaorong.assistant.study.service.StudyService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/study")
public class StudyController {
    private final StudyService studyService;

    public StudyController(StudyService studyService) { this.studyService = studyService; }

    @GetMapping("/courses")
    public Result<List<CourseSummary>> listCourses(@RequestParam(required = false) Long subjectId) {
        return Result.success(studyService.listCourses(subjectId));
    }

    @GetMapping("/overview")
    public Result<StudyOverviewResponse> getOverview() { return Result.success(studyService.getOverview()); }

    @GetMapping("/overview/advice")
    public Result<AdviceResponse> getAdvice() { return Result.success(studyService.getAdvice()); }

    @GetMapping("/ai/budget")
    public Result<TokenBudgetStatusResponse> getAiBudget() { return Result.success(studyService.getAiBudget()); }

    @PostMapping("/sessions")
    public Result<SessionCreateResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        return Result.success(studyService.createSession(request));
    }

    @GetMapping("/sessions/{sessionId}/script")
    public Result<LessonScriptResponse> getScript(@PathVariable Long sessionId) {
        return Result.success(studyService.getScript(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/nodes/{nodeId}/submit")
    public Result<NodeSubmitResponse> submitNode(@PathVariable Long sessionId, @PathVariable String nodeId,
                                                  @Valid @RequestBody SubmitAnswerRequest request) {
        return Result.success(studyService.submitNode(sessionId, nodeId, request));
    }

    @PostMapping("/sessions/{sessionId}/classmate/{nodeId}/submit")
    public Result<ClassmateSubmitResponse> submitClassmate(@PathVariable Long sessionId, @PathVariable String nodeId,
                                                            @Valid @RequestBody SubmitAnswerRequest request) {
        return Result.success(studyService.submitClassmate(sessionId, nodeId, request));
    }

    @PostMapping("/sessions/{sessionId}/ask")
    public Result<AskResponse> ask(@PathVariable Long sessionId, @Valid @RequestBody AskRequest request) {
        return Result.success(studyService.ask(sessionId, request));
    }

    @PostMapping(value = "/sessions/{sessionId}/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> askStream(@PathVariable Long sessionId, @Valid @RequestBody AskRequest request) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .body(studyService.askStream(sessionId, request));
    }

    @GetMapping("/sessions/{sessionId}/ai-reviews/{taskId}")
    public Result<AiReviewStatusResponse> getAiReview(@PathVariable Long sessionId, @PathVariable String taskId) {
        return Result.success(studyService.getAiReview(sessionId, taskId));
    }

    @PostMapping("/sessions/{sessionId}/interview/follow-up")
    public Result<InterviewFollowUpResponse> interviewFollowUp(@PathVariable Long sessionId,
                                                                @Valid @RequestBody InterviewFollowUpRequest request) {
        return Result.success(studyService.interviewFollowUp(sessionId, request));
    }

    @GetMapping("/sessions/{sessionId}/homework")
    public Result<HomeworkResponse> getHomework(@PathVariable Long sessionId) {
        return Result.success(studyService.getHomework(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/homework/{topicId}/submit")
    public Result<HomeworkSubmitResponse> submitHomework(@PathVariable Long sessionId, @PathVariable Long topicId,
                                                          @Valid @RequestBody SubmitAnswerRequest request) {
        return Result.success(studyService.submitHomework(sessionId, topicId, request));
    }

    @GetMapping("/sessions/{sessionId}/review")
    public Result<ReviewResponse> getReview(@PathVariable Long sessionId) {
        return Result.success(studyService.getReview(sessionId));
    }
}