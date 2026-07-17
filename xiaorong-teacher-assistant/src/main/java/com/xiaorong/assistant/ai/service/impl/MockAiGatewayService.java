package com.xiaorong.assistant.ai.service.impl;

import com.xiaorong.assistant.ai.dto.AiDtos.*;
import com.xiaorong.assistant.ai.service.AiGatewayService;
import com.xiaorong.assistant.ai.service.AiGatewayService.AiStreamListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class MockAiGatewayService implements AiGatewayService {

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        long startedAt = System.currentTimeMillis();
        String content = switch (request.scene()) {
            case "free-ask" -> "小绒老师：可以先抓住当前知识点的定义、解决的问题和边界，再结合一个实际例子复述。你的追问会保留最近三轮上下文，方便继续深入。";
            case "deep-review" -> "小绒老师深度讲评：你的答案已经有正确方向，但还需要补齐遗漏关键词，并说明这些关键词之间的因果关系。最后用参考答案的结构重新组织一次表达。";
            case "personal-review" -> "建议一：优先重刷薄弱标签对应题目，因为高频遗漏最影响稳定得分。建议二：每题压缩为三个关键词并口述，因为主动回忆比反复阅读更有效。建议三：隔天再做一次同类题，检查是否真正掌握。";
            case "interview-follow-up" -> "岚川面试官：如果这个场景放到真实项目里，你会怎么处理边界情况？";
            default -> "Mock AI 已收到场景：" + request.scene();
        };
        return new AiChatResponse(
                request.providerCode() == null ? "mock" : request.providerCode(),
                request.model() == null ? "mock-chat" : request.model(),
                request.scene(),
                content,
                0,
                content.length(),
                System.currentTimeMillis() - startedAt,
                true
        );
    }

    @Override
    public AiStructuredResponse structured(AiStructuredRequest request) {
        Map<String, Object> data = switch (request.schemaName()) {
            case "HomeworkReviewResult" -> Map.of(
                    "score", 86,
                    "hitKeywords", List.of("ref", ".value"),
                    "missKeywords", List.of("Proxy"),
                    "feedback", "答案方向正确，但缺少底层原理。",
                    "suggestion", "补充 reactive 基于 Proxy 的表达。"
            );
            case "StudyReviewResult" -> Map.of(
                    "teacherSummary", "本轮主干概念掌握不错，薄弱点集中在响应式边界。",
                    "classmateReply", "我也把这些关键词记下来了，我们下次一起复盘。",
                    "weakTags", List.of("Proxy", "toRefs")
            );
            default -> Map.of("message", "schema 已预留：" + request.schemaName());
        };
        return new AiStructuredResponse(request.schemaName(), data, true);
    }

    @Override
    public SseEmitter stream(AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(30_000L);
        stream(request, new AiStreamListener() {
            @Override
            public void onDelta(String text) {
                try {
                    emitter.send(SseEmitter.event().name("delta").data(Map.of("text", text)));
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }

            @Override
            public void onComplete(AiChatResponse response) {
                try {
                    emitter.send(SseEmitter.event().name("done").data(Map.of(
                            "providerCode", response.providerCode(),
                            "model", response.model(),
                            "degraded", true
                    )));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }

            @Override
            public void onError(Throwable cause) {
                emitter.completeWithError(cause);
            }
        });
        return emitter;
    }

    @Override
    public void stream(AiChatRequest request, AiStreamListener listener) {
        Thread streamThread = new Thread(() -> {
            try {
                for (String chunk : List.of("\u5c0f\u7ed2\u8001\u5e08\uff1a", "\u5f53\u524d\u4f7f\u7528\u6f14\u793a\u6a21\u5f0f\uff0c", "\u914d\u7f6e\u4e2a\u4eba Provider \u540e\u4f1a\u5b9e\u65f6\u8fd4\u56de\u6a21\u578b\u5185\u5bb9\u3002")) {
                    listener.onDelta(chunk);
                    Thread.sleep(120);
                }
                listener.onComplete(new AiChatResponse(
                        request.providerCode() == null ? "mock" : request.providerCode(),
                        request.model() == null ? "mock-chat" : request.model(),
                        request.scene(), "", 0, 0, 0L, true));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                listener.onError(ex);
            } catch (RuntimeException ex) {
                listener.onError(ex);
            }
        }, "mock-ai-gateway-stream");
        streamThread.setDaemon(true);
        streamThread.start();
    }

}

