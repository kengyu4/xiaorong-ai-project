package com.xiaorong.assistant.study.content;

import com.xiaorong.assistant.study.dto.StudyDtos.ClassmateView;
import com.xiaorong.assistant.study.dto.StudyDtos.CourseSummary;
import com.xiaorong.assistant.study.dto.StudyDtos.LessonNode;
import com.xiaorong.assistant.study.dto.StudyDtos.PersonaView;

import java.util.List;

public record StudyMaterial(
        CourseSummary course,
        PersonaView teacher,
        ClassmateView classmate,
        List<LessonNode> nodes,
        List<HomeworkSeed> homework
) {
    public record HomeworkSeed(
            Long topicId,
            String title,
            String body,
            List<String> tags,
            String difficulty,
            List<String> keywords,
            String standardAnswer
    ) {
    }
}
