package com.xiaorong.assistant.study.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class StudyDtos {
    private StudyDtos() {}

    public record CourseSummary(Long courseId, String title, String description, String difficulty, List<String> tags,
                                Integer lessonCount, Integer homeworkCount, String teacherAvatar, String classmateAvatar) {}
    public record CreateSessionRequest(@NotNull Long courseId, String mode) {}
    public record SessionCreateResponse(Long sessionId, Long courseId, String status, Integer currentNodeIndex) {}
    public record PersonaView(String name, String avatar, String role) {}
    public record ClassmateView(String name, String avatar, Integer bondValue) {}
    public record Reward(Integer rightBond, Integer wrongBond) {}
    public record LessonNode(String nodeId, String type, String speaker, String title, String text, String knowledgePoint,
                             String question, String answerType, List<String> answerKeywords, String standardAnswer,
                             String explanation, Reward reward) {}
    public record LessonScriptResponse(Long courseId, String title, PersonaView teacher, ClassmateView classmate,
                                       List<LessonNode> nodes) {}
    public record SubmitAnswerRequest(@NotBlank String answerText) {}
    public record NodeSubmitResponse(Integer score, List<String> hitKeywords, List<String> missKeywords, String feedback,
                                     String teacherReply, Integer nextNodeIndex, Boolean needAiReview,
                                     String aiReviewTaskId) {}
    public record ClassmateSubmitResponse(Integer score, Integer bondDelta, Integer bondValue, List<String> hitKeywords,
                                          List<String> missKeywords, String classmateReply, String teacherSupplement) {}
    public record AskRequest(@NotBlank String nodeId, @NotBlank String question) {}
    public record AskResponse(String answer, List<String> relatedKeywords, Boolean degraded,
                              String providerCode, String model) {
        public AskResponse(String answer, List<String> relatedKeywords, Boolean degraded) {
            this(answer, relatedKeywords, degraded, null, null);
        }
    }
    public record HomeworkResponse(List<HomeworkItem> items) {}
    public record HomeworkItem(Long topicId, String title, String body, List<String> tags, String difficulty) {}
    public record HomeworkSubmitResponse(Integer score, List<String> hitKeywords, List<String> missKeywords,
                                         String feedback, String aiReview, String standardAnswer, Boolean needAiReview,
                                         String aiReviewTaskId) {}
    public record NextCourse(Long courseId, String title) {}
    public record ReviewResponse(Integer averageScore, Integer bondValue, List<String> weakTags, String summary,
                                 String teacherSummary, String classmateReply, List<String> nextActions,
                                 NextCourse nextCourse, Boolean degraded, String providerCode, String model) {
        public ReviewResponse(Integer averageScore, Integer bondValue, List<String> weakTags, String summary,
                              String teacherSummary, String classmateReply, List<String> nextActions,
                              NextCourse nextCourse) {
            this(averageScore, bondValue, weakTags, summary, teacherSummary, classmateReply, nextActions,
                    nextCourse, true, null, null);
        }
    }
    public record StudyOverviewResponse(String topWeakTag, List<String> weakTags, Integer weakTagCount,
                                        Integer completedCount) {}
    public record AdviceResponse(List<String> suggestions, String teacherSummary, Integer averageScore,
                                 List<String> weakTags, List<String> courseTitles, Boolean hasLearningData,
                                 Boolean degraded, String providerCode, String model) {
        public AdviceResponse(List<String> suggestions, Integer averageScore, List<String> weakTags,
                              List<String> courseTitles, Boolean degraded, String providerCode, String model) {
            this(suggestions, suggestions == null ? "" : String.join("; ", suggestions), averageScore,
                    weakTags, courseTitles, weakTags != null && !weakTags.isEmpty(), degraded, providerCode, model);
        }
    }
    public record AiReviewStatusResponse(String taskId, String status, String content, Boolean degraded,
                                         String providerCode, String model) {
        public AiReviewStatusResponse(String taskId, String status, String content, Boolean degraded) {
            this(taskId, status, content, degraded, null, null);
        }
    }
    public record InterviewFollowUpRequest(@NotBlank String nodeId, @NotBlank String answerText,
                                           @Min(0) @Max(2) Integer followUpLevel) {}
    public record InterviewFollowUpResponse(String mode, String question, Integer followUpLevel) {}
    public record TokenBudgetStatusResponse(String date, Long requests, Long promptTokens, Long completionTokens,
                                            Long totalTokens, Long degradedRequests, Boolean warning,
                                            Boolean exhausted, Long warningLimit, Long hardLimit) {}
}
