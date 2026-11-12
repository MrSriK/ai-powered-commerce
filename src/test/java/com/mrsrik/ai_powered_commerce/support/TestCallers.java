package com.mrsrik.ai_powered_commerce.support;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.mrsrik.ai_powered_commerce.user.entity.User;
import com.mrsrik.ai_powered_commerce.user.enums.Role;

/** Creates callers (user row plus a real signed token) for web integration tests. */
@Component
public class TestCallers {

    /** A caller: {@code userId} is null when no user row exists. */
    public record Caller(UUID userId, String sub, String token, String bearer) {
    }

    private static final Consumer<TestTokens.Builder> NONE = b -> {
    };

    private final TestData testData;

    public TestCallers(TestData testData) {
        this.testData = testData;
    }

    public Caller registeredCustomer() {
        return registeredCustomer(NONE);
    }

    public Caller registeredCustomer(Consumer<TestTokens.Builder> tokenCustomizer) {
        return persisted(Role.CUSTOMER, null, tokenCustomizer);
    }

    public Caller registeredAdmin() {
        return registeredAdmin(NONE);
    }

    public Caller registeredAdmin(Consumer<TestTokens.Builder> tokenCustomizer) {
        return persisted(Role.ADMIN, null, tokenCustomizer);
    }

    /** Valid token whose subject matches no user; nothing is persisted. */
    public Caller unregistered() {
        return unregistered(NONE);
    }

    public Caller unregistered(Consumer<TestTokens.Builder> tokenCustomizer) {
        return caller(null, "auth0|unregistered-" + UUID.randomUUID(), tokenCustomizer);
    }

    /** Soft-deleted customer with a valid token. */
    public Caller disabledCustomer() {
        return disabledCustomer(NONE);
    }

    public Caller disabledCustomer(Consumer<TestTokens.Builder> tokenCustomizer) {
        return persisted(Role.CUSTOMER, Instant.now(), tokenCustomizer);
    }

    private Caller persisted(Role role, Instant deletedAt, Consumer<TestTokens.Builder> tokenCustomizer) {
        String sub = "auth0|" + UUID.randomUUID();
        User user = testData.user(b -> b.role(role).externalAuthId(sub).deletedAt(deletedAt));
        return caller(user.getId(), sub, tokenCustomizer);
    }

    private Caller caller(UUID userId, String sub, Consumer<TestTokens.Builder> tokenCustomizer) {
        TestTokens.Builder builder = TestTokens.token().subject(sub);
        tokenCustomizer.accept(builder);
        String token = builder.build();
        return new Caller(userId, sub, token, TestTokens.bearer(token));
    }
}
