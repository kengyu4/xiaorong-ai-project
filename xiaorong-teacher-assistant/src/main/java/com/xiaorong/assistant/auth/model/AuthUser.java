package com.xiaorong.assistant.auth.model;

import java.util.List;

public record AuthUser(
        Long userId,
        String username,
        String nickname,
        String passwordHash,
        String status,
        List<String> roles
) {
    public boolean active() {
        return "active".equalsIgnoreCase(status);
    }
}
