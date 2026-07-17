package com.xiaorong.assistant.ai.material;

public record CourseMaterialGenerationJob(
        String taskId,
        Long materialId,
        Long courseId,
        boolean force,
        String promptVersion
) {
}
