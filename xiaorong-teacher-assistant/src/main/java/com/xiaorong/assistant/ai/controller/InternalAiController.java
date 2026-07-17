package com.xiaorong.assistant.ai.controller;

import com.xiaorong.assistant.ai.dto.AiDtos.*;
import com.xiaorong.assistant.ai.service.AiGatewayService;
import com.xiaorong.assistant.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/internal/ai")
public class InternalAiController {
    private final AiGatewayService aiGatewayService;

    public InternalAiController(AiGatewayService aiGatewayService) {
        this.aiGatewayService = aiGatewayService;
    }

    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return Result.success(aiGatewayService.chat(request));
    }

    @PostMapping("/structured")
    public Result<AiStructuredResponse> structured(@Valid @RequestBody AiStructuredRequest request) {
        return Result.success(aiGatewayService.structured(request));
    }

    @PostMapping("/chat/stream")
    public SseEmitter stream(@Valid @RequestBody AiChatRequest request) {
        return aiGatewayService.stream(request);
    }
}
