package com.designmd.platform.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器：所有异常统一返回 Result<T> 结构。
 * HTTP 状态码语义：业务错误 400、未登录 401、无权限 403、资源不存在 404、系统异常 500；
 * Result.code 与 HTTP 状态码保持一致。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常（BizException）：code 默认 400，可自定义 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<?>> handleBizException(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        int status = e.getCode() >= 400 && e.getCode() < 600 ? e.getCode() : HttpStatus.BAD_REQUEST.value();
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    /** 兼容历史：service 层抛出的 IllegalArgumentException 视为业务错误 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<?>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Result.fail(400, e.getMessage()));
    }

    /** 参数校验失败（@Valid 生效时） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", message);
        return ResponseEntity.badRequest().body(Result.fail(400, message));
    }

    /** 404：请求的资源不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<?>> handleNotFound(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail(404, "请求的资源不存在"));
    }

    /** 运行时异常兜底 */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<?>> handleRuntime(RuntimeException e) {
        log.error("运行时异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(500, e.getMessage()));
    }

    /** 系统异常兜底 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(500, "服务器内部错误"));
    }
}
