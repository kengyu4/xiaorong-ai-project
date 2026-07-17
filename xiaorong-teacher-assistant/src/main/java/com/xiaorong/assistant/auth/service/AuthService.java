package com.xiaorong.assistant.auth.service;

import com.xiaorong.assistant.auth.AuthContext;
import com.xiaorong.assistant.auth.dto.AuthDtos.AuthResponse;
import com.xiaorong.assistant.auth.dto.AuthDtos.AuthUserView;
import com.xiaorong.assistant.auth.dto.AuthDtos.LoginRequest;
import com.xiaorong.assistant.auth.dto.AuthDtos.RegisterRequest;
import com.xiaorong.assistant.auth.exception.UnauthorizedException;
import com.xiaorong.assistant.auth.model.AuthSession;
import com.xiaorong.assistant.auth.model.AuthUser;
import com.xiaorong.assistant.auth.persistence.AuthJdbcRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {
    private final AuthJdbcRepository repository;
    private final PasswordService passwordService;
    private final AuthTokenService tokenService;

    public AuthService(AuthJdbcRepository repository,
                       PasswordService passwordService,
                       AuthTokenService tokenService) {
        this.repository = repository;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        String nickname = normalizeNickname(request.nickname(), username);
        if (repository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("用户名已存在");
        }

        try {
            AuthUser user = repository.insertUser(
                    username,
                    nickname,
                    passwordService.hash(request.password()),
                    List.of("student")
            );
            String token = tokenService.createToken(user);
            return new AuthResponse(token, toView(user));
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String username = normalizeUsername(request.username());
        AuthUser user = repository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("用户名或密码错误"));

        if (!user.active()) {
            throw new UnauthorizedException("账号已被禁用");
        }
        if (!passwordService.matches(request.password(), user.passwordHash())) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        repository.updateLastLogin(user.userId());
        String token = tokenService.createToken(user);
        return new AuthResponse(token, toView(user));
    }

    public AuthUserView currentUser() {
        return toView(AuthContext.require());
    }

    public void logout(String authorizationHeader) {
        tokenService.revoke(tokenService.extractBearerToken(authorizationHeader));
    }

    private AuthUserView toView(AuthUser user) {
        return new AuthUserView(user.userId(), user.username(), user.nickname(), user.roles());
    }

    private AuthUserView toView(AuthSession session) {
        return new AuthUserView(session.userId(), session.username(), session.nickname(), session.roles());
    }

    private String normalizeUsername(String value) {
        String username = value == null ? "" : value.trim();
        if (username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        return username;
    }

    private String normalizeNickname(String value, String username) {
        String nickname = value == null ? "" : value.trim();
        return nickname.isBlank() ? username : nickname;
    }
}
