package com.mrsrik.ai_powered_commerce.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.mrsrik.ai_powered_commerce.ai.provider.AiProvider;
import com.mrsrik.ai_powered_commerce.ai.provider.AiProviderException;
import com.mrsrik.ai_powered_commerce.ai.provider.AiReply;
import com.mrsrik.ai_powered_commerce.ai.provider.AiRequest;

/**
 * Deterministic test implementation of {@link AiProvider} (Requirements 11.4, 7.9).
 *
 * <p>Registered as a {@code @Primary} bean in the test context so integration tests
 * run completely offline without calling an external LLM service.
 */
@Component
@Primary
public class FakeAiProvider implements AiProvider {

    public static final String DEFAULT_REPLY =
            "I would recommend checking out the items in our catalog that match your description.";

    private final List<AiRequest> requests = Collections.synchronizedList(new ArrayList<>());
    private volatile String reply = DEFAULT_REPLY;
    private volatile RuntimeException failureException = null;
    private volatile Duration delay = null;
    private volatile boolean shouldBlock = false;

    @Override
    public AiReply generateReply(AiRequest request) throws AiProviderException {
        requests.add(request);

        if (failureException != null) {
            if (failureException instanceof AiProviderException ape) {
                throw ape;
            }
            throw new AiProviderException("Fake AI provider failed", failureException);
        }

        if (shouldBlock) {
            try {
                // Block for longer than the 30-second service timeout
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiProviderException("Fake AI provider interrupted while blocking", e);
            }
        }

        if (delay != null && !delay.isZero()) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiProviderException("Fake AI provider interrupted during delay", e);
            }
        }

        return new AiReply(reply);
    }

    public void setReply(String reply) {
        this.reply = reply != null ? reply : DEFAULT_REPLY;
    }

    public void setFailure(RuntimeException exception) {
        this.failureException = exception;
    }

    public void setDelay(Duration delay) {
        this.delay = delay;
    }

    public void setShouldBlock(boolean shouldBlock) {
        this.shouldBlock = shouldBlock;
    }

    public AiRequest getLastRequest() {
        if (requests.isEmpty()) {
            return null;
        }
        return requests.get(requests.size() - 1);
    }

    public List<AiRequest> getAllRequests() {
        return List.copyOf(requests);
    }

    public int getRequestCount() {
        return requests.size();
    }

    public void reset() {
        requests.clear();
        reply = DEFAULT_REPLY;
        failureException = null;
        delay = null;
        shouldBlock = false;
    }
}
