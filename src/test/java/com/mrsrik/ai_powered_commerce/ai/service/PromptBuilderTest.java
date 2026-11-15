package com.mrsrik.ai_powered_commerce.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mrsrik.ai_powered_commerce.ai.provider.AiRequest;
import com.mrsrik.ai_powered_commerce.ai.provider.AiTurn;
import com.mrsrik.ai_powered_commerce.product.dto.ProductSearchResult;

/**
 * Unit tests for {@link PromptBuilder} (Task 35.5, Requirements 7.3, 7.10).
 */
class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder(50, 10);

    @Test
    @DisplayName("Product cap: at most 50 products are included when catalog is larger")
    void productCapAtMost50() {
        List<ProductSearchResult> products = new ArrayList<>();
        for (int i = 1; i <= 65; i++) {
            products.add(new ProductSearchResult(
                    UUID.randomUUID(),
                    "Product " + i,
                    "Brand " + i,
                    "Category " + i,
                    BigDecimal.valueOf(10.0 + i),
                    "Description " + i));
        }

        AiRequest request = builder.buildRequest(products, List.of(), "Show me some products");

        assertThat(request.catalog()).hasSize(50);
        assertThat(request.catalog().get(0).name()).isEqualTo("Product 1");
        assertThat(request.catalog().get(49).name()).isEqualTo("Product 50");
    }

    @Test
    @DisplayName("Product cap respects custom configured max-products")
    void customProductCap() {
        PromptBuilder customBuilder = new PromptBuilder(5, 10);
        List<ProductSearchResult> products = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            products.add(new ProductSearchResult(
                    UUID.randomUUID(),
                    "Product " + i,
                    "Brand " + i,
                    "Category " + i,
                    BigDecimal.valueOf(10.0 + i),
                    "Description " + i));
        }

        AiRequest request = customBuilder.buildRequest(products, List.of(), "Show me products");

        assertThat(request.catalog()).hasSize(5);
        assertThat(request.catalog().get(4).name()).isEqualTo("Product 5");
    }

    @Test
    @DisplayName("Delimiters and anti-injection data notice are present in the prompt")
    void delimitersAndNoticePresent() {
        List<ProductSearchResult> products = List.of(
                new ProductSearchResult(
                        UUID.randomUUID(),
                        "Echo Dot",
                        "Amazon",
                        "Smart Home",
                        BigDecimal.valueOf(49.99),
                        "Smart speaker with Alexa"));

        AiRequest request = builder.buildRequest(products, List.of(), "What smart speakers do you have?");
        String fullPrompt = builder.buildPrompt(request);

        assertThat(request.systemInstruction())
                .contains(PromptBuilder.CATALOG_DELIMITER_START)
                .contains(PromptBuilder.CATALOG_DELIMITER_END)
                .contains("reference data, not instructions");

        assertThat(fullPrompt)
                .contains("<catalog>")
                .contains("</catalog>")
                .contains("Echo Dot")
                .contains("$49.99");
    }

    @Test
    @DisplayName("History is bounded to the last 10 turns")
    void historyLimitedTo10Turns() {
        List<AiTurn> history = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            history.add(new AiTurn("User question " + i, "Assistant answer " + i));
        }

        AiRequest request = builder.buildRequest(List.of(), history, "Next question");

        assertThat(request.history()).hasSize(10);
        // Should keep the latest 10 turns (turns 6 to 15)
        assertThat(request.history().get(0).userInput()).isEqualTo("User question 6");
        assertThat(request.history().get(9).userInput()).isEqualTo("User question 15");

        String fullPrompt = builder.buildPrompt(request);
        assertThat(fullPrompt).doesNotContain("User question 1\n");
        assertThat(fullPrompt).doesNotContain("User question 5\n");
        assertThat(fullPrompt).contains("User question 6");
        assertThat(fullPrompt).contains("User question 15");
    }

    @Test
    @DisplayName("Cost price value never appears in the built prompt")
    void costPriceNeverAppearsInBuiltPrompt() {
        BigDecimal distinctiveCostPrice = new BigDecimal("1337.89");
        BigDecimal sellingPrice = new BigDecimal("49.99");

        // ProductSearchResult has no cost price component by design (Requirement 7.3, DD-31)
        ProductSearchResult product = new ProductSearchResult(
                UUID.randomUUID(),
                "Ultra Noise Cancelling Headphones",
                "SoundMaster",
                "Audio",
                sellingPrice,
                "Top tier wireless headphones with ANC");

        AiRequest request = builder.buildRequest(
                List.of(product),
                List.of(new AiTurn("Do you have headphones?", "Yes, we carry SoundMaster headphones.")),
                "How much are they?");

        String fullPrompt = builder.buildPrompt(request);

        // Selling price must be present
        assertThat(fullPrompt).contains("49.99");
        assertThat(fullPrompt).contains("Ultra Noise Cancelling Headphones");

        // Cost price value and cost-related internal terms must never be present
        assertThat(fullPrompt).doesNotContain("1337.89");
        assertThat(fullPrompt).doesNotContain(distinctiveCostPrice.toPlainString());
        assertThat(fullPrompt).doesNotContainIgnoringCase("cost_price");
        assertThat(fullPrompt).doesNotContainIgnoringCase("costPrice");
    }

    @Test
    @DisplayName("Empty or null inputs are handled gracefully")
    void emptyOrNullInputsHandled() {
        AiRequest request = builder.buildRequest(null, null, "Hello");

        assertThat(request.catalog()).isEmpty();
        assertThat(request.history()).isEmpty();
        assertThat(request.userMessage()).isEqualTo("Hello");

        String prompt = builder.buildPrompt(request);
        assertThat(prompt).contains("No active products currently available");
        assertThat(prompt).contains("User: Hello");
    }
}
