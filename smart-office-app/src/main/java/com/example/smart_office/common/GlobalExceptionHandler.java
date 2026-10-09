package com.example.smart_office.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
//
//    @ExceptionHandler(RuntimeException.class)
//    public Result<String> handleRuntimeException(RuntimeException e) {
//        // 捕获所有 RuntimeException，返回统一的错误格式
//        return Result.error(500, e.getMessage());
//    }

    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "系统繁忙，请检查数据库";
        System.out.println("=== 全局异常捕获: " + msg + " ===");
        return Result.error(500, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        return Result.error(500, "系统繁忙，请稍后重试");
    }

}