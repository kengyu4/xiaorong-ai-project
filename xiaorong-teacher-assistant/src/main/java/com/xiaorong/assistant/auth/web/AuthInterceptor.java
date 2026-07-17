package com.xiaorong.assistant.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.auth.AuthContext;
import com.xiaorong.assistant.auth.model.AuthSession;
import com.xiaorong.assistant.auth.service.AuthTokenService;
import com.xiaorong.assistant.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthTokenService tokenService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(AuthTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = tokenService.extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        Optional<AuthSession> session = tokenService.validate(token);
        if (session.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), Result.unauthorized("登录已过期，请重新登录"));
            return false;
        }

        AuthContext.set(session.get());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }
}
