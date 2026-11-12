package com.mrsrik.ai_powered_commerce.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mrsrik.ai_powered_commerce.support.AbstractPostgresIT;
import com.mrsrik.ai_powered_commerce.user.entity.User;

class UserRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findsActiveUserByExternalAuthId() {
        User user = testData.user(b -> b.externalAuthId("auth0|active"));

        assertThat(userRepository.findByExternalAuthId("auth0|active"))
                .hasValueSatisfying(found -> {
                    assertThat(found.getId()).isEqualTo(user.getId());
                    assertThat(found.getDeletedAt()).isNull();
                });
    }

    @Test
    void findsSoftDeletedUserByExternalAuthId() {
        testData.user(b -> b.externalAuthId("auth0|gone").deletedAt(Instant.now()));

        assertThat(userRepository.findByExternalAuthId("auth0|gone"))
                .hasValueSatisfying(found -> assertThat(found.getDeletedAt()).isNotNull());
    }

    @Test
    void returnsEmptyForUnknownExternalAuthId() {
        testData.user();

        assertThat(userRepository.findByExternalAuthId("auth0|unknown")).isEmpty();
    }

    @Test
    void existsByExternalAuthIdIncludesSoftDeleted() {
        testData.user(b -> b.externalAuthId("auth0|a"));
        testData.user(b -> b.externalAuthId("auth0|b").deletedAt(Instant.now()));

        assertThat(userRepository.existsByExternalAuthId("auth0|a")).isTrue();
        assertThat(userRepository.existsByExternalAuthId("auth0|b")).isTrue();
        assertThat(userRepository.existsByExternalAuthId("auth0|c")).isFalse();
    }

    @Test
    void existsByEmailIgnoreCaseMatchesAnyCaseAndSoftDeleted() {
        testData.user(b -> b.email("Mixed.Case@example.test"));
        testData.user(b -> b.email("deleted@example.test").deletedAt(Instant.now()));

        assertThat(userRepository.existsByEmailIgnoreCase("mixed.case@EXAMPLE.test")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("DELETED@example.test")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("nobody@example.test")).isFalse();
    }
}
