package com.xiaorong.assistant.ai.service;

import com.xiaorong.assistant.ai.dto.AiDtos.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiGatewayService {
    AiChatResponse chat(AiChatRequest request);

    AiStructuredResponse structured(AiStructuredRequest request);

    SseEmitter stream(AiChatRequest request);

    /**
     * Streams provider deltas to the caller. Implementations that support an upstream streaming protocol must
     * invoke this listener as each upstream delta arrives instead of waiting for the whole response.
     */
    default void stream(AiChatRequest request, AiStreamListener listener) {
        try {
            AiChatResponse response = chat(request);
            if (response.content() != null && !response.content().isBlank()) {
                listener.onDelta(response.content());
            }
            listener.onComplete(response);
        } catch (RuntimeException ex) {
            listener.onError(ex);
        }
    }

    interface AiStreamListener {
        void onDelta(String text);

        void onComplete(AiChatResponse response);

        void onError(Throwable cause);
    }
}
