package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class AiProviderException extends ApiException {

    public AiProviderException() {
        this("The AI provider is unavailable");
    }

    public AiProviderException(String message) {
        super(ErrorCode.AI_PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY, message);
    }
}
