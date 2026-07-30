package com.smartmed.backend.common;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.Resource;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * 全局异常处理器。HTTP status line 恒 200（ADR-0003），业务错误靠 body.code 区分。
 * <p>
 * 映射表见 02 ticket 实现约束。
 * 注意：401（未登录/token 缺失）由 {@code JwtAuthFilter} 直接写响应，不进此处理器；
 * 403（越权，{@link AccessDeniedException}）由此处理器捕获。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Resource
    private ObjectMapper objectMapper;

    /** 参数校验失败：@Valid RequestBody。返回字段级明细。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fe = ex.getBindingResult().getFieldError();
        String msg = fe == null ? "参数校验失败" : fe.getField() + ": " + fe.getDefaultMessage();
        return Result.error(400, msg);
    }

    /** 参数校验失败：@Validated 路径/查询参数。返回字段级明细。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        ConstraintViolation<?> cv = ex.getConstraintViolations().stream().findFirst().orElse(null);
        String msg = cv == null ? "参数校验失败" : cv.getPropertyPath() + ": " + cv.getMessage();
        return Result.error(400, msg);
    }

    /** 参数类型不匹配。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return Result.error(400, "参数类型错误: " + ex.getName());
    }

    /** 业务异常：code 可自定义。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException ex) {
        log.warn("业务异常: code={}, msg={}", ex.getCode(), ex.getMessage());
        return Result.error(ex.getCode(), ex.getMessage());
    }

    /** 越权：@PreAuthorize 抛出。code=403。 */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException ex) {
        return Result.error(403, "无权访问");
    }

    /** 兜底：未捕获异常。code=500，不泄露堆栈。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception ex) {
        log.error("未捕获异常", ex);
        return Result.error(500, "服务器内部错误");
    }

    /**
     * 将统一响应体写入 HttpServletResponse（供 JwtAuthFilter 在拦截链外直接写 401）。
     * HTTP status 仍恒 200（ADR-0003）。
     */
    public void writeJson(HttpServletResponse response, Result<Void> result) {
        try {
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            log.error("写入响应失败", e);
        }
    }
}
