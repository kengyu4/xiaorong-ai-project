package com.xiaorong.assistant.ai.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatResponse;
import com.xiaorong.assistant.ai.dto.AiDtos.AiMessage;
import com.xiaorong.assistant.ai.service.AiGatewayService.AiStreamListener;
import com.xiaorong.assistant.config.XiaorongProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Component
public class OpenAiCompatibleAdapter implements AiProviderAdapter {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public OpenAiCompatibleAdapter(RestClient.Builder builder, ObjectMapper objectMapper) {
        this(builder, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build());
    }

    OpenAiCompatibleAdapter(RestClient.Builder builder) {
        this(builder, new ObjectMapper(), HttpClient.newHttpClient());
    }

    OpenAiCompatibleAdapter(RestClient.Builder builder, ObjectMapper objectMapper, HttpClient httpClient) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String providerCode() {
        return "openai-compatible";
    }

    @Override
    public boolean supports(String protocol, String scene) {
        return "openai-compatible".equals(protocol);
    }

    @Override
    public AiChatResponse chat(AiChatRequest request, XiaorongProperties.Provider provider) {
        requireApiKey(provider);
        long startedAt = System.currentTimeMillis();
        String model = selectedModel(request, provider);
        Map<String, Object> body = requestBody(request, model, false);
        Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri(chatCompletionsUrl(provider.getBaseUrl()))
                    .header("Authorization", "Bearer " + provider.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException ex) {
            throw providerRequestFailure(provider, ex);
        }
        String content = readContent(response);
        Map<?, ?> usage = response == null ? Map.of() : map(response.get("usage"));
        return new AiChatResponse(
                provider.getProviderCode(),
                responseModel(response, model),
                request.scene(),
                content,
                intValue(usage.get("prompt_tokens")),
                intValue(usage.get("completion_tokens")),
                System.currentTimeMillis() - startedAt,
                false
        );
    }

    /**
     * Uses the provider's OpenAI-compatible Server-Sent Events endpoint. Each valid `data:` frame is forwarded
     * as soon as it is received; the complete answer is never buffered before the first client delta is sent.
     */
    @Override
    public void stream(AiChatRequest request, XiaorongProperties.Provider provider, AiStreamListener listener) {
        requireApiKey(provider);
        String model = selectedModel(request, provider);
        Thread streamThread = new Thread(() -> streamUpstream(request, provider, model, listener),
                "openai-compatible-stream-" + provider.getProviderCode());
        streamThread.setDaemon(true);
        streamThread.start();
    }

    @Override
    public List<String> listModels(XiaorongProperties.Provider provider) {
        requireApiKey(provider);
        Map<?, ?> response;
        try {
            response = restClient.get()
                    .uri(modelsUrl(provider.getBaseUrl()))
                    .header("Authorization", "Bearer " + provider.getApiKey())
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException ex) {
            throw providerRequestFailure(provider, ex);
        }
        Object dataObject = response == null ? null : response.get("data");
        if (!(dataObject instanceof List<?> data)) {
            return List.of();
        }
        return data.stream()
                .map(this::map)
                .map(item -> item.get("id"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private void streamUpstream(AiChatRequest request, XiaorongProperties.Provider provider, String selectedModel,
                                AiStreamListener listener) {
        long startedAt = System.currentTimeMillis();
        AtomicBoolean terminal = new AtomicBoolean(false);
        StreamState state = new StreamState(selectedModel);
        try {
            String body = objectMapper.writeValueAsString(requestBody(request, selectedModel, true));
            HttpRequest upstreamRequest = HttpRequest.newBuilder(URI.create(chatCompletionsUrl(provider.getBaseUrl())))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + provider.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Stream<String>> response = httpClient.send(upstreamRequest, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("upstream returned non-success status");
            }
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> readStreamLine(line, state, listener));
            }
            if (terminal.compareAndSet(false, true)) {
                listener.onComplete(new AiChatResponse(
                        provider.getProviderCode(), state.model(), request.scene(), "",
                        state.promptTokens(), state.completionTokens(), System.currentTimeMillis() - startedAt, false));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            failStream(listener, terminal, provider, ex);
        } catch (IOException | RuntimeException ex) {
            failStream(listener, terminal, provider, ex);
        }
    }

    private void readStreamLine(String line, StreamState state, AiStreamListener listener) {
        if (line == null || !line.startsWith("data:")) return;
        String data = line.substring("data:".length()).trim();
        if (data.isBlank() || "[DONE]".equals(data)) return;
        try {
            Map<String, Object> frame = objectMapper.readValue(data, MAP_TYPE);
            state.capture(frame);
            String delta = readDelta(frame);
            if (!delta.isBlank()) {
                listener.onDelta(delta);
            }
        } catch (IOException ignored) {
            // Ignore one malformed provider frame; a subsequent valid frame or stream completion can still succeed.
        }
    }

    private void failStream(AiStreamListener listener, AtomicBoolean terminal,
                            XiaorongProperties.Provider provider, Exception cause) {
        if (terminal.compareAndSet(false, true)) {
            listener.onError(providerRequestFailure(provider, cause));
        }
    }

    private void requireApiKey(XiaorongProperties.Provider provider) {
        if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            throw new IllegalArgumentException("Provider " + provider.getProviderCode() + " ??? apiKey");
        }
    }

    private String selectedModel(AiChatRequest request, XiaorongProperties.Provider provider) {
        return request.model() == null || request.model().isBlank()
                ? provider.getDefaultModel()
                : request.model();
    }

    private Map<String, Object> requestBody(AiChatRequest request, String model, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages(request.messages()));
        body.put("temperature", request.temperature() == null ? 0.4 : request.temperature());
        if (request.maxTokens() != null) {
            body.put("max_tokens", request.maxTokens());
        }
        if ("json_object".equals(request.responseFormat())) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    private List<Map<String, String>> messages(List<AiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of(Map.of("role", "user", "content", "\u4f60\u597d"));
        }
        return messages.stream()
                .map(message -> Map.of(
                        "role", message.role() == null || message.role().isBlank() ? "user" : message.role(),
                        "content", message.content() == null ? "" : message.content()
                ))
                .toList();
    }

    private String chatCompletionsUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Provider baseUrl \u4e0d\u80fd\u4e3a\u7a7a");
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private String modelsUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Provider baseUrl \u4e0d\u80fd\u4e3a\u7a7a");
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/models")) {
            return normalized;
        }
        return normalized + "/models";
    }

    private IllegalStateException providerRequestFailure(
            XiaorongProperties.Provider provider, Exception cause
    ) {
        return new IllegalStateException("Provider " + provider.getProviderCode() + " \u8bf7\u6c42\u5931\u8d25");
    }

    private String readContent(Map<?, ?> response) {
        if (response == null) {
            return "";
        }
        Object choicesObject = response.get("choices");
        if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) {
            return "";
        }
        Map<?, ?> choice = map(choices.get(0));
        Map<?, ?> message = map(choice.get("message"));
        Object content = message.get("content");
        return content == null ? "" : String.valueOf(content);
    }

    private String readDelta(Map<String, Object> frame) {
        Object choicesObject = frame.get("choices");
        if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) return "";
        Map<?, ?> choice = map(choices.get(0));
        Map<?, ?> delta = map(choice.get("delta"));
        Object content = delta.get("content");
        return content instanceof String text ? text : "";
    }

    private String responseModel(Map<?, ?> response, String fallback) {
        if (response == null || response.get("model") == null) return fallback;
        String value = String.valueOf(response.get("model")).trim();
        return value.isBlank() ? fallback : value;
    }

    private Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private final class StreamState {
        private String model;
        private int promptTokens;
        private int completionTokens;

        private StreamState(String model) {
            this.model = model;
        }

        private void capture(Map<String, Object> frame) {
            Object responseModel = frame.get("model");
            if (responseModel != null && !String.valueOf(responseModel).isBlank()) {
                model = String.valueOf(responseModel);
            }
            Map<?, ?> usage = map(frame.get("usage"));
            promptTokens = intValue(usage.get("prompt_tokens"));
            completionTokens = intValue(usage.get("completion_tokens"));
        }

        private String model() { return model; }

        private int promptTokens() { return promptTokens; }

        private int completionTokens() { return completionTokens; }
    }
}
