package com.xiaorong.assistant.ai.adapter;

import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatResponse;
import com.xiaorong.assistant.ai.service.AiGatewayService.AiStreamListener;
import com.xiaorong.assistant.config.XiaorongProperties;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface AiProviderAdapter {
    String providerCode();

    boolean supports(String protocol, String scene);

    AiChatResponse chat(AiChatRequest request, XiaorongProperties.Provider provider);

    default List<String> listModels(XiaorongProperties.Provider provider) {
        return List.of();
    }

    /**
     * Callback-oriented streaming hook. Concrete adapters should deliver every upstream delta immediately.
     * The default exists only for legacy adapters that do not expose an upstream stream protocol.
     */
    default void stream(AiChatRequest request, XiaorongProperties.Provider provider, AiStreamListener listener) {
        try {
            AiChatResponse response = chat(request, provider);
            if (response.content() != null && !response.content().isBlank()) {
                listener.onDelta(response.content());
            }
            listener.onComplete(response);
        } catch (RuntimeException ex) {
            listener.onError(ex);
        }
    }

    default SseEmitter stream(AiChatRequest request, XiaorongProperties.Provider provider) {
        SseEmitter emitter = new SseEmitter(60_000L);
        stream(request, provider, new AiStreamListener() {
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
                            "degraded", Boolean.TRUE.equals(response.mock())
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
}
