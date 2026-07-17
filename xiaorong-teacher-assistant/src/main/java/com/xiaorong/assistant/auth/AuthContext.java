package com.xiaorong.assistant.auth;

import com.xiaorong.assistant.auth.exception.UnauthorizedException;
import com.xiaorong.assistant.auth.model.AuthSession;

import java.util.Optional;

public final class AuthContext {
    private static final ThreadLocal<AuthSession> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthSession session) {
        CURRENT.set(session);
    }

    public static Optional<AuthSession> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static AuthSession require() {
        return current().orElseThrow(() -> new UnauthorizedException("请先登录"));
    }

    public static Long requireUserId() {
        return require().userId();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
