package com.smartmed.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应体（CONTEXT §2：{@code { code, message, data }}）。
 * <p>
 * HTTP status line 恒 200，业务错误靠 {@link #code} 区分数值语义（ADR-0003 Consequences）。
 * code 取值为 HTTP 状态码（200/400/401/403/500）。
 *
 * @param code    业务码（数值语义同 HTTP 状态码）
 * @param message 提示信息
 * @param data    业务数据，无数据时为 null（序列化省略 null 字段）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Result<T>(int code, String message, T data) {

    /** 成功响应，code=200。 */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /** 成功响应无数据，code=200。 */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    /** 错误响应，自定义 code 与 message。 */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
