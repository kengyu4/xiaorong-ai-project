package com.xiaorong.assistant.ai.adapter;

import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatResponse;
import com.xiaorong.assistant.ai.dto.AiDtos.AiMessage;
import com.xiaorong.assistant.ai.service.AiGatewayService.AiStreamListener;
import com.xiaorong.assistant.config.XiaorongProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class OpenAiCompatibleAdapterTest {

    @Test
    void listsSortedDistinctModelsWithBearerAuthorization() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(builder);
        XiaorongProperties.Provider provider = provider("sk-user-secret");
        server.expect(requestTo("https://example.com/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-user-secret"))
                .andRespond(withSuccess("""
                        {"data":[
                          {"id":"z-model"},
                          {"id":"a-model"},
                          {"id":"z-model"},
                          {"id":""},
                          {"owned_by":"missing-id"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<String> models = adapter.listModels(provider);

        assertThat(models).containsExactly("a-model", "z-model");
        server.verify();
    }

    @Test
    void streamsEveryOpenAiCompatibleSseDeltaBeforeCompletion() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread upstream = new Thread(() -> writeSseResponse(server), "test-openai-sse-server");
            upstream.start();
            OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(RestClient.builder());
            XiaorongProperties.Provider provider = provider("sk-user-secret");
            provider.setBaseUrl("http://127.0.0.1:" + server.getLocalPort() + "/v1");
            List<String> chunks = new ArrayList<>();
            AtomicReference<AiChatResponse> completed = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);

            adapter.stream(new AiChatRequest("free-ask", null, null,
                    List.of(new AiMessage("user", "hello")), 0.3, 1000, true, null), provider,
                    new AiStreamListener() {
                        @Override
                        public void onDelta(String text) { chunks.add(text); }

                        @Override
                        public void onComplete(AiChatResponse response) {
                            completed.set(response);
                            done.countDown();
                        }

                        @Override
                        public void onError(Throwable cause) {
                            done.countDown();
                            throw new AssertionError(cause);
                        }
                    });

            assertThat(done.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(chunks).containsExactly("?", "??");
            assertThat(completed.get()).isNotNull();
            assertThat(completed.get().providerCode()).isEqualTo("deepseek");
            assertThat(completed.get().model()).isEqualTo("deepseek-chat");
            assertThat(completed.get().promptTokens()).isEqualTo(12);
            assertThat(completed.get().completionTokens()).isEqualTo(8);
            upstream.join(1_000L);
        }
    }

    @Test
    void upstreamFailureMessageNeverContainsApiKeyOrResponseBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(builder);
        XiaorongProperties.Provider provider = provider("sk-user-secret");
        server.expect(requestTo("https://example.com/v1/models"))
                .andRespond(withUnauthorizedRequest().body("upstream echoed sk-user-secret"));

        assertThatThrownBy(() -> adapter.listModels(provider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("请求失败")
                .hasMessageNotContaining("sk-user-secret")
                .hasMessageNotContaining("upstream echoed");
        server.verify();
    }

    private void writeSseResponse(ServerSocket server) {
        try (Socket socket = server.accept();
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream output = socket.getOutputStream()) {
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Consume request headers before writing the event stream.
            }
            String events = "data: {\"model\":\"deepseek-chat\",\"choices\":[{\"delta\":{\"content\":\"?\"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"??\"}}]}\n\n"
                    + "data: {\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8}}\n\n"
                    + "data: [DONE]\n\n";
            byte[] bytes = events.getBytes(StandardCharsets.UTF_8);
            output.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/event-stream; charset=utf-8\r\n"
                    + "Content-Length: " + bytes.length + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(bytes);
            output.flush();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private XiaorongProperties.Provider provider(String apiKey) {
        XiaorongProperties.Provider provider = new XiaorongProperties.Provider();
        provider.setProviderCode("deepseek");
        provider.setProtocol("openai-compatible");
        provider.setBaseUrl("https://example.com/v1/");
        provider.setApiKey(apiKey);
        provider.setDefaultModel("deepseek-chat");
        provider.setEnabled(true);
        return provider;
    }
}
