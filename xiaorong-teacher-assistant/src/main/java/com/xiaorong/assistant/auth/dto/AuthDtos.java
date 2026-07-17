package com.xiaorong.assistant.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 30, message = "用户名长度需要在 3-30 位之间")
            String username,

            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 72, message = "密码长度需要在 6-72 位之间")
            String password
    ) {
    }

    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 30, message = "用户名长度需要在 3-30 位之间")
            @Pattern(regexp = "^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$", message = "用户名只能包含中文、字母、数字、下划线和短横线")
            String username,

            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 72, message = "密码长度需要在 6-72 位之间")
            String password,

            @Size(max = 30, message = "昵称最多 30 个字符")
            String nickname
    ) {
    }

    public record AuthUserView(
            Long userId,
            String username,
            String nickname,
            List<String> roles
    ) {
    }

    public record AuthResponse(
            String token,
            AuthUserView user
    ) {
    }
}
