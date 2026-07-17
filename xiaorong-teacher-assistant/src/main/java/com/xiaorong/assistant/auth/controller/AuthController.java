package com.xiaorong.assistant.auth.controller;

import com.xiaorong.assistant.auth.dto.AuthDtos.AuthResponse;
import com.xiaorong.assistant.auth.dto.AuthDtos.AuthUserView;
import com.xiaorong.assistant.auth.dto.AuthDtos.LoginRequest;
import com.xiaorong.assistant.auth.dto.AuthDtos.RegisterRequest;
import com.xiaorong.assistant.auth.service.AuthService;
import com.xiaorong.assistant.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/me")
    public Result<AuthUserView> me() {
        return Result.success(authService.currentUser());
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                               HttpServletRequest request) {
        authService.logout(authorizationHeader == null ? request.getHeader("authorization") : authorizationHeader);
        return Result.success();
    }
}
