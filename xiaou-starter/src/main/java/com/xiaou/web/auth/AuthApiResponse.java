package com.xiaou.web.auth;

public record AuthApiResponse<T>(int status, String message, T data) {

    public static <T> AuthApiResponse<T> success(String message, T data) {
        return new AuthApiResponse<>(200, message, data);
    }

    public static <T> AuthApiResponse<T> failure(int status, String message) {
        return new AuthApiResponse<>(status, message, null);
    }
}
