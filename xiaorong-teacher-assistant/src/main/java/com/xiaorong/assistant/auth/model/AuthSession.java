package com.xiaorong.assistant.auth.model;

import java.util.List;

public record AuthSession(
        Long userId,
        String username,
        String nickname,
        List<String> roles
) {
}
