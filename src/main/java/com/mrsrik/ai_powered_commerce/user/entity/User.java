package com.mrsrik.ai_powered_commerce.user.entity;

import java.time.Instant;
import java.util.UUID;

import com.mrsrik.ai_powered_commerce.user.enums.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "external_auth_id", unique = true)
    private String externalAuthId;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
