package com.xiaorong.assistant.ai.controller;

import com.xiaorong.assistant.ai.prompt.StudyPromptTemplates;
import com.xiaorong.assistant.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai/prompts")
public class PromptController {

    @GetMapping
    public Result<Map<String, String>> previewPrompts() {
        return Result.success(StudyPromptTemplates.preview());
    }
}
