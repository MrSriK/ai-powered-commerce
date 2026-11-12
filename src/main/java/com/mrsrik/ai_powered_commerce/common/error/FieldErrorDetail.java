package com.mrsrik.ai_powered_commerce.common.error;

/** One invalid request field: its name, the violated constraint, and a client-safe message. */
public record FieldErrorDetail(String field, String constraint, String message) {
}
