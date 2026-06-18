package com.testdatagen.exception;

/**
 * 认证异常：登录失败、Token 无效等
 */
public class AuthException extends RuntimeException {

    private final int statusCode;

    public AuthException(String message) {
        super(message);
        this.statusCode = 401;
    }

    public AuthException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}
