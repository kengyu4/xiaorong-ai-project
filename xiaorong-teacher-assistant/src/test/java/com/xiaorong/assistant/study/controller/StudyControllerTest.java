package com.xiaorong.assistant.study.controller;

import com.xiaorong.assistant.common.Result;
import com.xiaorong.assistant.study.dto.StudyDtos.AdviceResponse;
import com.xiaorong.assistant.study.dto.StudyDtos.AskRequest;
import com.xiaorong.assistant.study.service.StudyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudyControllerTest {
    @Test
    void exposesCurrentUserOverviewAdviceEndpoint() {
        StudyService studyService = mock(StudyService.class);
        AdviceResponse advice = new AdviceResponse(
                List.of("重刷 Proxy", "复述边界"), 68, List.of("Proxy"), List.of("Vue 响应式"),
                false, "deepseek", "deepseek-chat");
        when(studyService.getAdvice()).thenReturn(advice);
        StudyController controller = new StudyController(studyService);

        Result<AdviceResponse> result = controller.getAdvice();

        assertThat(result.getData()).isSameAs(advice);
        assertThat(result.getData().toString()).doesNotContain("apiKey", "secret");
    }
    @Test
    void streamEndpointDisablesBufferingAndCaching() {
        StudyService studyService = mock(StudyService.class);
        SseEmitter emitter = new SseEmitter();
        AskRequest request = new AskRequest("node-1", "Proxy ???");
        when(studyService.askStream(101L, request)).thenReturn(emitter);
        StudyController controller = new StudyController(studyService);

        ResponseEntity<SseEmitter> response = controller.askStream(101L, request);

        assertThat(response.getBody()).isSameAs(emitter);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-cache");
        assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
    }

}
