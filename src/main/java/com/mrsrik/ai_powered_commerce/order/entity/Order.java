package com.mrsrik.ai_powered_commerce.order.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", insertable = false, updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "shipping_name", nullable = false, length = 255)
    private String shippingName;

    @Column(name = "shipping_phone_number", nullable = false, length = 15)
    private String shippingPhoneNumber;

    @Column(name = "shipping_address_line_1", nullable = false)
    private String shippingAddressLine1;

    @Column(name = "shipping_address_line_2")
    private String shippingAddressLine2;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String state;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(name = "transaction_id", nullable = false, insertable = false, updatable = false)
    private String transactionId;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "total_items", nullable = false)
    private Integer totalItems;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
