package com.designmd.platform.common;

import lombok.Getter;

/**
 * 业务异常：由业务层主动抛出，GlobalExceptionHandler 统一捕获并转换为 Result 结构。
 * code 与 HTTP 状态码语义保持一致：默认 400（业务错误）。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
