package com.mrsrik.ai_powered_commerce.ai.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mrsrik.ai_powered_commerce.ai.dto.AiMessageDto;
import com.mrsrik.ai_powered_commerce.ai.dto.ConversationDto;
import com.mrsrik.ai_powered_commerce.ai.dto.CreateConversationRequest;
import com.mrsrik.ai_powered_commerce.ai.dto.SendMessageRequest;
import com.mrsrik.ai_powered_commerce.ai.service.ShoppingAssistantService;
import com.mrsrik.ai_powered_commerce.security.AuthenticatedUser;
import com.mrsrik.ai_powered_commerce.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * REST controller for the AI shopping assistant (Requirements 7.1, 7.2, 7.4, 7.5, 7.7, 8.7).
 */
@RestController
@RequestMapping(path = "/api/ai/conversations", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
public class AiController {

    private final ShoppingAssistantService shoppingAssistantService;

    public AiController(ShoppingAssistantService shoppingAssistantService) {
        this.shoppingAssistantService = shoppingAssistantService;
    }

    private UUID getUserId(AuthenticatedUser user) {
        return user != null ? user.userId() : CurrentUser.id();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDto startConversation(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody(required = false) CreateConversationRequest request) {
        return shoppingAssistantService.startConversation(
                getUserId(user),
                request != null ? request : new CreateConversationRequest(null));
    }

    @GetMapping
    public List<ConversationDto> listConversations(@AuthenticationPrincipal AuthenticatedUser user) {
        return shoppingAssistantService.listConversations(getUserId(user));
    }

    @PostMapping(path = "/{id}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AiMessageDto sendMessage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        return shoppingAssistantService.sendMessage(getUserId(user), id, request);
    }

    @GetMapping(path = "/{id}/messages")
    public List<AiMessageDto> history(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        return shoppingAssistantService.history(getUserId(user), id);
    }
}
