package com.mrsrik.ai_powered_commerce.user.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import com.mrsrik.ai_powered_commerce.user.entity.User;
import com.mrsrik.ai_powered_commerce.user.enums.Role;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates the registration DTOs with a plain Bean Validation validator. The
 * ignored-role test uses the
 * JsonMapper that Spring Boot auto-configures through {@code @JsonTest}.
 */
@JsonTest
class RegisterUserRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @Autowired
    private JsonMapper mapper;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> invalidFields(RegisterUserRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static RegisterUserRequest valid() {
        return new RegisterUserRequest("Ada", "Lovelace", "ada@x.test", "5551234", Role.CUSTOMER);
    }

    @Test
    void validRequestHasNoViolations() {
        assertTrue(validator.validate(valid()).isEmpty());
    }

    @Test
    void nameLimitsAtBoundary() {
        assertTrue(
                invalidFields(new RegisterUserRequest("a".repeat(255), "b".repeat(255), "a@b.test", null, Role.CUSTOMER)).isEmpty());
        assertEquals(Set.of("firstName", "lastName"),
                invalidFields(new RegisterUserRequest("a".repeat(256), "b".repeat(256), "a@b.test", null, Role.CUSTOMER)));
    }

    @Test
    void emailLimitsAtBoundary() {
        // Domain labels stay within 63 characters so only the length limit can fail.
        String label = "b".repeat(63);
        String at254 = "a".repeat(64) + "@" + label + "." + label + "." + "c".repeat(57) + ".com";
        String at255 = "a".repeat(64) + "@" + label + "." + label + "." + "c".repeat(58) + ".com";
        assertEquals(254, at254.length());
        assertEquals(255, at255.length());
        assertTrue(invalidFields(new RegisterUserRequest("A", "B", at254, null, Role.CUSTOMER)).isEmpty());
        assertTrue(invalidFields(new RegisterUserRequest("A", "B", at255, null, Role.CUSTOMER)).contains("email"));
    }

    @Test
    void phoneLimitsAtBoundary() {
        assertTrue(invalidFields(new RegisterUserRequest("A", "B", "a@b.test", "1".repeat(15), Role.CUSTOMER)).isEmpty());
        assertEquals(Set.of("phoneNumber"),
                invalidFields(new RegisterUserRequest("A", "B", "a@b.test", "1".repeat(16), Role.CUSTOMER)));
    }

    @Test
    void blankAndWhitespaceOnlyValuesViolateNotBlank() {
        assertEquals(Set.of("firstName", "lastName", "email"),
                invalidFields(new RegisterUserRequest("   ", "", "  ", null, Role.CUSTOMER)));
        assertEquals(Set.of("firstName", "lastName", "email", "role"),
                invalidFields(new RegisterUserRequest(null, null, null, null, null)));
    }

    @Test
    void malformedEmailViolatesEmail() {
        assertEquals(Set.of("email"), invalidFields(new RegisterUserRequest("A", "B", "not-an-email", null, Role.CUSTOMER)));
    }

    @Test
    void nullAndBlankPhoneAreAccepted() {
        assertNull(new RegisterUserRequest("A", "B", "a@b.test", null, Role.CUSTOMER).phoneNumber());
        assertNull(new RegisterUserRequest("A", "B", "a@b.test", "   ", Role.CUSTOMER).phoneNumber());
        assertTrue(invalidFields(new RegisterUserRequest("A", "B", "a@b.test", "   ", Role.CUSTOMER)).isEmpty());
    }

    @Test
    void trimsStrings() {
        RegisterUserRequest request = new RegisterUserRequest("  Ada ", " Lovelace  ", "  Ada@X.test ", " 555 ", Role.CUSTOMER);
        assertEquals("Ada", request.firstName());
        assertEquals("Lovelace", request.lastName());
        assertEquals("Ada@X.test", request.email());
        assertEquals("555", request.phoneNumber());
        assertEquals(Role.CUSTOMER, request.role());
    }

    @Test
    void reportsEveryInvalidFieldTogether() {
        assertEquals(Set.of("firstName", "lastName", "email", "phoneNumber", "role"),
                invalidFields(new RegisterUserRequest("", "x".repeat(256), "bad", "1".repeat(16), null)));
    }

    @Test
    void hasRoleComponent() {
        Set<String> names = Arrays.stream(RegisterUserRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("firstName", "lastName", "email", "phoneNumber", "role"), names);
        assertTrue(names.contains("role"));
    }

    @Test
    void rolePropertyIsParsedByBootMapper() throws Exception {
        RegisterUserRequest request = mapper.readValue(
                "{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"a@b.test\",\"role\":\"ADMIN\"}",
                RegisterUserRequest.class);

        assertEquals(new RegisterUserRequest("A", "B", "a@b.test", null, Role.ADMIN), request);
        assertTrue(validator.validate(request).isEmpty());
        assertTrue(mapper.writeValueAsString(request).contains("\"role\":\"ADMIN\""));
    }

    @Test
    void nullRoleFailsValidation() {
        RegisterUserRequest request = new RegisterUserRequest("A", "B", "a@b.test", null, null);
        assertEquals(Set.of("role"), invalidFields(request));
    }

    @Test
    void profileDtoMapsAllFieldsAndHasNoExternalAuthId() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2025-01-02T03:04:05Z");
        User user = User.builder()
                .id(id).firstName("Ada").lastName("Lovelace").email("ada@x.test").phoneNumber("555")
                .externalAuthId("sub-123").emailVerified(true).role(Role.CUSTOMER).createdAt(created)
                .deletedAt(Instant.now()).build();

        UserProfileDto dto = UserProfileDto.from(user);

        assertEquals(new UserProfileDto(id, "Ada", "Lovelace", "ada@x.test", "555", true, Role.CUSTOMER, created), dto);
        Set<String> names = Arrays.stream(UserProfileDto.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertFalse(names.contains("externalAuthId"));
        assertFalse(names.contains("deletedAt"));
    }
}
