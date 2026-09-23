package com.newvent.common.response;

public record ApiResponse<T>(boolean success, T data, String message) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    public static <T> ApiResponse<T> successNoData() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> successNoData(String message) {
        return new ApiResponse<>(true, null, message);
    }
}
