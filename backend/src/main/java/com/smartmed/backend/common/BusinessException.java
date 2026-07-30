package com.smartmed.backend.common;

import lombok.Getter;

/**
 * 业务异常。默认 code=400，允许传自定义 code 覆盖（如挂号冲突 409，留给后续 ticket）。
 * <p>
 * 被 {@link GlobalExceptionHandler} 捕获后转为统一响应体。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
