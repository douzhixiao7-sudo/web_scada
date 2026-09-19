package com.example.scada.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.scada.auth.AuthException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError auth(AuthException ex) {
        return ApiError.of(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badRequest(IllegalArgumentException ex) {
        return ApiError.of(ex.getMessage());
    }
}
