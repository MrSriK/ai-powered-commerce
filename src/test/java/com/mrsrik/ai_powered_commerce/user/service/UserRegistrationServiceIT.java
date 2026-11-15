package com.mrsrik.ai_powered_commerce.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;

import com.mrsrik.ai_powered_commerce.common.error.AlreadyRegisteredException;
import com.mrsrik.ai_powered_commerce.common.error.EmailAlreadyRegisteredException;
import com.mrsrik.ai_powered_commerce.common.error.UserNotFoundException;
import com.mrsrik.ai_powered_commerce.support.AbstractPostgresIT;
import com.mrsrik.ai_powered_commerce.user.dto.RegisterUserRequest;
import com.mrsrik.ai_powered_commerce.user.dto.UserProfileDto;
import com.mrsrik.ai_powered_commerce.user.enums.Role;

class UserRegistrationServiceIT extends AbstractPostgresIT {

    private static final int THREADS = 6;
    private static final int ROUNDS = 5;

    @Autowired
    private UserRegistrationService service;

    private static Jwt jwt(String sub, String email, Object emailVerified) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject(sub);
        if (email != null) {
            b.claim("email", email);
        }
        if (emailVerified != null) {
            b.claim("email_verified", emailVerified);
        }
        return b.build();
    }

    private static RegisterUserRequest request(String email) {
        return new RegisterUserRequest("Ada", "Lovelace", email, "+15550001111", Role.CUSTOMER);
    }

    private int count(String where, Object... args) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE " + where, Integer.class, args);
    }

    @Test
    void registrationPersistsOneCustomerRow() {
        UserProfileDto dto = service.register(jwt("auth0|one", "Ada@Example.test", true), request("Ada@Example.test"));

        assertThat(dto.role()).isEqualTo(Role.CUSTOMER);
        assertThat(dto.email()).isEqualTo("ada@example.test");
        assertThat(dto.emailVerified()).isTrue();
        assertThat(dto.createdAt()).isNotNull();
        assertThat(count("1=1")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role || '|' || email || '|' || external_auth_id FROM users", String.class))
                .isEqualTo("CUSTOMER|ada@example.test|auth0|one");
    }

    @Test
    void emailVerifiedIsFalseWithoutMatchingVerifiedClaim() {
        UserProfileDto dto = service.register(jwt("auth0|nv", "ada@example.test", false), request("ada@example.test"));
        assertThat(dto.emailVerified()).isFalse();
    }

    @Test
    void secondRegistrationWithSameSubIsRejected() {
        service.register(jwt("auth0|dup", null, null), request("a@example.test"));
        assertThatThrownBy(() -> service.register(jwt("auth0|dup", null, null), request("b@example.test")))
                .isInstanceOf(AlreadyRegisteredException.class);
        assertThat(count("external_auth_id = ?", "auth0|dup")).isEqualTo(1);
    }

    @Test
    void sameEmailInDifferentCaseFromOtherSubIsRejected() {
        service.register(jwt("auth0|a", null, null), request("same@example.test"));
        assertThatThrownBy(() -> service.register(jwt("auth0|b", null, null), request("SAME@Example.test")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(count("1=1")).isEqualTo(1);
    }

    @Test
    void softDeletedUsersEmailIsStillReserved() {
        testData.user(b -> b.email("gone@example.test").deletedAt(Instant.now()));
        assertThatThrownBy(() -> service.register(jwt("auth0|new", null, null), request("Gone@example.test")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(count("1=1")).isEqualTo(1);
    }

    @Test
    void concurrentRegistrationsWithSameSubHaveExactlyOneWinner() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String sub = "auth0|race-" + round + "-" + UUID.randomUUID();
            List<Callable<UserProfileDto>> tasks = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                String email = "same-sub-" + round + "-" + i + "@example.test";
                tasks.add(() -> service.register(jwt(sub, null, null), request(email)));
            }
            Outcome outcome = runConcurrently(tasks);
            assertThat(outcome.successes).isEqualTo(1);
            assertThat(outcome.alreadyRegistered).isEqualTo(THREADS - 1);
            assertThat(outcome.other).isEmpty();
            assertThat(count("external_auth_id = ?", sub)).isEqualTo(1);
        }
    }

    @Test
    void concurrentRegistrationsWithSameEmailHaveExactlyOneWinner() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String email = "same-email-" + round + "@example.test";
            List<Callable<UserProfileDto>> tasks = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                String sub = "auth0|e-" + round + "-" + i;
                String variant = i % 2 == 0 ? email : email.toUpperCase();
                tasks.add(() -> service.register(jwt(sub, null, null), request(variant)));
            }
            Outcome outcome = runConcurrently(tasks);
            assertThat(outcome.successes).isEqualTo(1);
            assertThat(outcome.emailRegistered).isEqualTo(THREADS - 1);
            assertThat(outcome.other).isEmpty();
            assertThat(count("email = ?", email)).isEqualTo(1);
        }
    }

    @Test
    void getProfileReturnsActiveUserAndRejectsDeletedOrMissing() {
        var active = testData.user();
        var deleted = testData.user(b -> b.deletedAt(Instant.now()));

        assertThat(service.getProfile(active.getId()).email()).isEqualTo(active.getEmail());
        assertThatThrownBy(() -> service.getProfile(deleted.getId())).isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> service.getProfile(UUID.randomUUID())).isInstanceOf(UserNotFoundException.class);
    }

    private static final class Outcome {
        int successes;
        int alreadyRegistered;
        int emailRegistered;
        final List<Throwable> other = new ArrayList<>();
    }

    /** Starts all tasks behind a latch so they hit the database at the same moment. */
    private static Outcome runConcurrently(List<Callable<UserProfileDto>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<UserProfileDto>> futures = new ArrayList<>();
            for (Callable<UserProfileDto> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();
            Outcome outcome = new Outcome();
            for (Future<UserProfileDto> f : futures) {
                try {
                    f.get();
                    outcome.successes++;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof AlreadyRegisteredException) {
                        outcome.alreadyRegistered++;
                    } else if (cause instanceof EmailAlreadyRegisteredException) {
                        outcome.emailRegistered++;
                    } else {
                        outcome.other.add(cause);
                    }
                }
            }
            return outcome;
        } finally {
            pool.shutdownNow();
        }
    }
}
