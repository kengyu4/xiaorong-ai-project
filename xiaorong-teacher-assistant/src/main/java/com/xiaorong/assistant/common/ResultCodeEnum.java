package com.xiaorong.assistant.common;

public enum ResultCodeEnum {
    SUCCESS(200, "success"),
    FAIL(500, "fail"),
    NOT_FOUND(404, "not found"),
    BAD_REQUEST(400, "bad request"),
    UNAUTHORIZED(401, "unauthorized"),
    FORBIDDEN(403, "forbidden");

    private final Integer code;
    private final String message;

    ResultCodeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
