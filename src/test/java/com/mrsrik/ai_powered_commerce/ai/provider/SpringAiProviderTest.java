package com.mrsrik.ai_powered_commerce.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;

/**
 * Unit tests for {@link SpringAiProvider} (Requirements 7.8, 7.9).
 */
class SpringAiProviderTest {

    private ChatClient chatClient;
    private ChatClientRequestSpec requestSpec;
    private CallResponseSpec responseSpec;
    private SpringAiProvider provider;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClientRequestSpec.class);
        responseSpec = mock(CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        provider = new SpringAiProvider(chatClient);
    }

    @Test
    @DisplayName("Successfully returns generated reply text from ChatClient")
    void generateReplySuccess() {
        when(responseSpec.content()).thenReturn("Here is the recommendation from Gemini.");

        AiRequest request = new AiRequest(
                "You are an assistant",
                List.of(new AiTurn("Hi", "Hello")),
                "What headphones do you have?",
                List.of());

        AiReply reply = provider.generateReply(request);

        assertThat(reply).isNotNull();
        assertThat(reply.text()).isEqualTo("Here is the recommendation from Gemini.");

        verify(requestSpec).system("You are an assistant");
        verify(requestSpec).messages(anyList());
    }

    @Test
    @DisplayName("Empty reply from ChatClient throws AiProviderException")
    void emptyReplyThrowsAiProviderException() {
        when(responseSpec.content()).thenReturn("");

        AiRequest request = new AiRequest("System", List.of(), "User message", List.of());

        assertThatThrownBy(() -> provider.generateReply(request))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("empty reply");
    }

    @Test
    @DisplayName("Null reply from ChatClient throws AiProviderException")
    void nullReplyThrowsAiProviderException() {
        when(responseSpec.content()).thenReturn(null);

        AiRequest request = new AiRequest("System", List.of(), "User message", List.of());

        assertThatThrownBy(() -> provider.generateReply(request))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("empty reply");
    }

    @Test
    @DisplayName("Underlying client exception is wrapped into AiProviderException")
    void underlyingExceptionWrappedIntoAiProviderException() {
        when(requestSpec.call()).thenThrow(new RuntimeException("Google GenAI connection refused"));

        AiRequest request = new AiRequest("System", List.of(), "User message", List.of());

        assertThatThrownBy(() -> provider.generateReply(request))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("AI provider failed to generate a reply")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
