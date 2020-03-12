package com.sxw.server.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException{
    private static final int DEFAULT_ERROR_CODE ;
    private int errorCode;
    private Object errorData;

    public BusinessException(int code, String message, Object errorData, Throwable cause) {
        super(message, cause);
        this.errorCode = (long)code == 0L ? DEFAULT_ERROR_CODE : code;
        this.errorData = errorData;
    }

    public BusinessException info() {
        return this;
    }

    static {
        DEFAULT_ERROR_CODE = HttpStatus.BAD_REQUEST.value();
    }

    public int getCode() {
        return this.errorCode;
    }

    public Object getData() {
        return this.errorData;
    }
}
