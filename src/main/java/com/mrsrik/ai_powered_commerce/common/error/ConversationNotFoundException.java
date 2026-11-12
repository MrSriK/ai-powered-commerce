package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class ConversationNotFoundException extends ApiException {

    public ConversationNotFoundException() {
        this("Conversation not found");
    }

    public ConversationNotFoundException(String message) {
        super(ErrorCode.CONVERSATION_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
