package com.mrsrik.ai_powered_commerce.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrsrik.ai_powered_commerce.ai.provider.AiProviderException;
import com.mrsrik.ai_powered_commerce.common.error.ErrorCode;
import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.support.AbstractWebIT;
import com.mrsrik.ai_powered_commerce.support.FakeAiProvider;
import com.mrsrik.ai_powered_commerce.support.TestCallers.Caller;

/**
 * End-to-end integration tests for the AI shopping assistant (Requirement 7, 11.3, 11.4).
 */
class AiConversationFlowIT extends AbstractWebIT {

    private static final String CONVERSATIONS_URL = "/api/ai/conversations";

    @Autowired
    private FakeAiProvider fakeAiProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetFakeProvider() {
        fakeAiProvider.reset();
    }

    @Test
    @DisplayName("Create conversation: returns 201 with id and createdAt timestamp")
    void createConversationReturns201() throws Exception {
        Caller customer = callers.registeredCustomer();

        // 1. With title
        perform(post(CONVERSATIONS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Tech Recommendations\"}"), customer)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title", is("Tech Recommendations")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        // 2. Without title (empty body)
        perform(post(CONVERSATIONS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"), customer)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title", nullValue()))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    @DisplayName("Send and receive message: returns 200, records turn, and feeds catalog to provider")
    void sendAndReceiveMessage() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category(b -> b.name("Audio"));
        testData.product(category.getId(), b -> b
                .name("Wireless ANC Headphones")
                .brand("SoundPro")
                .sellingPrice(new BigDecimal("129.99"))
                .stock(20));

        UUID conversationId = createConversation(customer, "Headphones inquiry");

        fakeAiProvider.setReply("I recommend the Wireless ANC Headphones by SoundPro for $129.99.");

        perform(post(CONVERSATIONS_URL + "/" + conversationId + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Do you have wireless noise cancelling headphones?\"}"), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.conversationId", is(conversationId.toString())))
                .andExpect(jsonPath("$.userInput", is("Do you have wireless noise cancelling headphones?")))
                .andExpect(jsonPath("$.aiOutput", is("I recommend the Wireless ANC Headphones by SoundPro for $129.99.")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        assertThat(fakeAiProvider.getLastRequest()).isNotNull();
        assertThat(fakeAiProvider.getLastRequest().userMessage())
                .isEqualTo("Do you have wireless noise cancelling headphones?");
        assertThat(fakeAiProvider.getLastRequest().catalog())
                .anyMatch(p -> p.name().equals("Wireless ANC Headphones"));
    }

    @Test
    @DisplayName("History: returns empty list when no messages, returns chronological messages when present")
    void historyOrdering() throws Exception {
        Caller customer = callers.registeredCustomer();
        UUID conversationId = createConversation(customer, "Order check");

        // Empty history initially
        perform(get(CONVERSATIONS_URL + "/" + conversationId + "/messages"), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));

        // Send first message
        fakeAiProvider.setReply("Reply 1");
        perform(post(CONVERSATIONS_URL + "/" + conversationId + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Question 1\"}"), customer)
                .andExpect(status().isOk());

        // Send second message
        fakeAiProvider.setReply("Reply 2");
        perform(post(CONVERSATIONS_URL + "/" + conversationId + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Question 2\"}"), customer)
                .andExpect(status().isOk());

        // History ordered ascending by createdAt
        perform(get(CONVERSATIONS_URL + "/" + conversationId + "/messages"), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].userInput", is("Question 1")))
                .andExpect(jsonPath("$[0].aiOutput", is("Reply 1")))
                .andExpect(jsonPath("$[1].userInput", is("Question 2")))
                .andExpect(jsonPath("$[1].aiOutput", is("Reply 2")));
    }

    @Test
    @DisplayName("List conversations: scoped to user and ordered by createdAt descending")
    void listConversationsScopedToUser() throws Exception {
        Caller customerA = callers.registeredCustomer();
        Caller customerB = callers.registeredCustomer();

        UUID convA1 = createConversation(customerA, "Customer A Conv 1");
        UUID convA2 = createConversation(customerA, "Customer A Conv 2");
        UUID convB1 = createConversation(customerB, "Customer B Conv 1");

        // Customer A sees only their conversations, latest first
        perform(get(CONVERSATIONS_URL), customerA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(convA2.toString())))
                .andExpect(jsonPath("$[1].id", is(convA1.toString())));

        // Customer B sees only their conversation
        perform(get(CONVERSATIONS_URL), customerB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(convB1.toString())));
    }

    @Test
    @DisplayName("Another user's or unknown conversation gives 404 and persists nothing")
    void unknownOrForeignConversationReturns404() throws Exception {
        Caller customerA = callers.registeredCustomer();
        Caller customerB = callers.registeredCustomer();
        UUID convA = createConversation(customerA, "Customer A Private Chat");
        UUID unknownId = UUID.randomUUID();

        // 1. Unknown conversation on get messages -> 404
        perform(get(CONVERSATIONS_URL + "/" + unknownId + "/messages"), customerA)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(ErrorCode.CONVERSATION_NOT_FOUND.name())));

        // 2. Unknown conversation on post message -> 404
        perform(post(CONVERSATIONS_URL + "/" + unknownId + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Hello unknown\"}"), customerA)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(ErrorCode.CONVERSATION_NOT_FOUND.name())));

        // 3. User B accessing User A's conversation on get messages -> 404
        perform(get(CONVERSATIONS_URL + "/" + convA + "/messages"), customerB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(ErrorCode.CONVERSATION_NOT_FOUND.name())));

        // 4. User B sending message to User A's conversation -> 404
        perform(post(CONVERSATIONS_URL + "/" + convA + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Attempted intrusion\"}"), customerB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(ErrorCode.CONVERSATION_NOT_FOUND.name())));

        assertThat(fakeAiProvider.getRequestCount()).isZero();

        // Verify User A's conversation history remains empty
        perform(get(CONVERSATIONS_URL + "/" + convA + "/messages"), customerA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    @DisplayName("Blank message gives 400 and provider is not invoked")
    void blankMessageGives400() throws Exception {
        Caller customer = callers.registeredCustomer();
        UUID conv = createConversation(customer, "Blank message test");

        perform(post(CONVERSATIONS_URL + "/" + conv + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"   \"}"), customer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(ErrorCode.VALIDATION_FAILED.name())));

        perform(post(CONVERSATIONS_URL + "/" + conv + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"\"}"), customer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(ErrorCode.VALIDATION_FAILED.name())));

        assertThat(fakeAiProvider.getRequestCount()).isZero();

        perform(get(CONVERSATIONS_URL + "/" + conv + "/messages"), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    @DisplayName("Provider failure gives 502 with nothing persisted")
    void providerFailureReturns502() throws Exception {
        Caller customer = callers.registeredCustomer();
        UUID conv = createConversation(customer, "Failure test");

        fakeAiProvider.setFailure(new AiProviderException("Simulated LLM outage"));

        perform(post(CONVERSATIONS_URL + "/" + conv + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Will this fail?\"}"), customer)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code", is(ErrorCode.AI_PROVIDER_UNAVAILABLE.name())));

        // Conversation history remains untouched
        perform(get(CONVERSATIONS_URL + "/" + conv + "/messages"), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    @DisplayName("Provider timeout gives 502 with nothing persisted")
    void providerTimeoutReturns502() throws Exception {
        Caller customer = callers.registeredCustomer();
        UUID conv = createConversation(customer, "Timeout test");

        fakeAiProvider.setShouldBlock(true);

        perform(post(CONVERSATIONS_URL + "/" + conv + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Will this time out?\"}"), customer)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code", is(ErrorCode.AI_PROVIDER_UNAVAILABLE.name())));

        // Conversation history remains untouched
        perform(get(CONVERSATIONS_URL + "/" + conv + "/messages"), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    @DisplayName("Very long message round-trips successfully")
    void veryLongMessageRoundTrips() throws Exception {
        Caller customer = callers.registeredCustomer();
        UUID conv = createConversation(customer, "Long message test");

        String longMessage = "Please explain the specs of this item. ".repeat(300); // ~12,000 characters
        fakeAiProvider.setReply("Here is the detailed explanation for your extensive inquiry.");

        perform(post(CONVERSATIONS_URL + "/" + conv + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SendMessageRequest(longMessage))), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userInput", is(longMessage)))
                .andExpect(jsonPath("$.aiOutput", is("Here is the detailed explanation for your extensive inquiry.")));

        // Retrieve history and verify long content persisted accurately
        perform(get(CONVERSATIONS_URL + "/" + conv + "/messages"), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userInput", is(longMessage)));
    }

    private UUID createConversation(Caller customer, String title) throws Exception {
        String payload = title != null ? "{\"title\":\"" + title + "\"}" : "{}";
        MvcResult result = perform(post(CONVERSATIONS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload), customer)
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(node.get("id").asText());
    }

    private record SendMessageRequest(String message) {}
}
