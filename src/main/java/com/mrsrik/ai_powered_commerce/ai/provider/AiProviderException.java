package com.mrsrik.ai_powered_commerce.ai.provider;

import org.springframework.http.HttpStatus;

import com.mrsrik.ai_powered_commerce.common.error.ApiException;
import com.mrsrik.ai_powered_commerce.common.error.ErrorCode;

public class AiProviderException extends ApiException {

    public AiProviderException() {
        this("The AI provider is unavailable");
    }

    public AiProviderException(String message) {
        super(ErrorCode.AI_PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY, message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(ErrorCode.AI_PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY, message, cause);
    }

    public AiProviderException(Throwable cause) {
        this("The AI provider is unavailable", cause);
    }
}
