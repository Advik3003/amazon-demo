package com.amazondemo.android.model;

/**
 * Generic API Response wrapper - matches the backend ApiResponse<T>
 */
public class ApiResponse<T> {
    private String status;
    private String message;
    private T data;
    private Object errors;

    public boolean isSuccess() { return "SUCCESS".equals(status); }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public Object getErrors() { return errors; }
}
