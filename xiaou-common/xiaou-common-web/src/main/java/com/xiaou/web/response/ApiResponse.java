package com.xiaou.web.response;

public record ApiResponse<T>(int status, String message, T data) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return success(200, message, data);
    }

    public static <T> ApiResponse<T> success(int status, String message, T data) {
        return new ApiResponse<>(status, message, data);
    }

    public static <T> ApiResponse<T> failure(int status, String message) {
        return new ApiResponse<>(status, message, null);
    }
}
