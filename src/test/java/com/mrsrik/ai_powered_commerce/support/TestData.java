package com.mrsrik.ai_powered_commerce.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.mrsrik.ai_powered_commerce.cart.entity.Cart;
import com.mrsrik.ai_powered_commerce.cart.entity.CartItem;
import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.entity.OrderItem;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.product.entity.ProductImage;
import com.mrsrik.ai_powered_commerce.user.entity.User;
import com.mrsrik.ai_powered_commerce.user.enums.Role;

import jakarta.persistence.EntityManager;

/**
 * Persists valid test rows and returns them with database-managed columns
 * (createdAt, updatedAt) populated. Foreign keys are always set through the
 * scalar
 * id fields, never through associations (D-48). Defaults satisfy all CHECK and
 * UNIQUE
 * constraints. Each method has an overload taking a customizer for the entity
 * builder.
 */
@Component
public class TestData {

    private static final AtomicLong SEQ = new AtomicLong();

    private final EntityManager entityManager;
    private final TransactionTemplate tx;

    public TestData(EntityManager entityManager, TransactionTemplate tx) {
        this.entityManager = entityManager;
        this.tx = tx;
    }

    private static long next() {
        return SEQ.incrementAndGet();
    }

    public User user() {
        return user(b -> {
        });
    }

    public User user(Role role) {
        return user(b -> b.role(role));
    }

    public User user(Consumer<User.UserBuilder> customizer) {
        long n = next();
        User.UserBuilder b = User.builder()
                .firstName("First" + n)
                .lastName("Last" + n)
                .email("user" + n + "@example.test")
                .phoneNumber("+1555" + String.format("%07d", n % 10_000_000))
                .externalAuthId("auth0|test-" + n)
                .emailVerified(true)
                .role(Role.CUSTOMER);
        customizer.accept(b);
        return save(b.build());
    }

    public Category category() {
        return category(b -> {
        });
    }

    public Category category(Consumer<Category.CategoryBuilder> customizer) {
        Category.CategoryBuilder b = Category.builder().name("Category " + next());
        customizer.accept(b);
        return save(b.build());
    }

    public Product product(UUID categoryId) {
        return product(categoryId, b -> {
        });
    }

    public Product product(UUID categoryId, Consumer<Product.ProductBuilder> customizer) {
        long n = next();
        Product.ProductBuilder b = Product.builder()
                .categoryId(categoryId)
                .name("Product " + n)
                .description("Description " + n)
                .brand("Brand")
                .sku("SKU-" + n)
                .costPrice(new BigDecimal("5.00"))
                .sellingPrice(new BigDecimal("10.00"))
                .stock(100)
                .active(true);
        customizer.accept(b);
        return save(b.build());
    }

    public ProductImage productImage(UUID productId, int displayOrder) {
        return productImage(productId, displayOrder, b -> {
        });
    }

    public ProductImage productImage(UUID productId, int displayOrder,
            Consumer<ProductImage.ProductImageBuilder> customizer) {
        ProductImage.ProductImageBuilder b = ProductImage.builder()
                .productId(productId)
                .displayOrder(displayOrder)
                .imageUrl("https://images.example.test/" + next() + ".png");
        customizer.accept(b);
        return save(b.build());
    }

    public Cart cart(UUID userId) {
        return save(Cart.builder().userId(userId).build());
    }

    public CartItem cartItem(UUID cartId, UUID productId, int quantity) {
        return save(CartItem.builder().cartId(cartId).productId(productId).quantity(quantity).build());
    }

    public Order order(UUID userId, OrderStatus status) {
        return order(userId, status, b -> {
        });
    }

    public Order order(UUID userId, OrderStatus status, Consumer<Order.OrderBuilder> customizer) {
        long n = next();
        Order.OrderBuilder b = Order.builder()
                .userId(userId)
                .shippingName("Ship Name " + n)
                .shippingPhoneNumber("+15550001111")
                .shippingAddressLine1("1 Main Street")
                .city("Springfield")
                .state("IL")
                .postalCode("62701")
                .country("US")
                .transactionId("txn-" + n)
                .totalAmount(new BigDecimal("20.00"))
                .totalItems(2)
                .status(status);
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) {
            // chk_orders_shipped_at_required (V9) needs shipped_at for these statuses.
            b.shippedAt(Instant.now());
        }
        customizer.accept(b);
        return save(b.build());
    }

    public OrderItem orderItem(UUID orderId, UUID productId, int quantity) {
        return orderItem(orderId, productId, quantity, b -> {
        });
    }

    public OrderItem orderItem(UUID orderId, UUID productId, int quantity,
            Consumer<OrderItem.OrderItemBuilder> customizer) {
        long n = next();
        OrderItem.OrderItemBuilder b = OrderItem.builder()
                .orderId(orderId)
                .productId(productId)
                .productName("Product " + n)
                .brand("Brand")
                .sku("SKU-OI-" + n)
                .unitPrice(new BigDecimal("10.00"))
                .quantity(quantity);
        customizer.accept(b);
        return save(b.build());
    }

    /** Persist, flush and refresh so database-managed columns are populated. */
    private <T> T save(T entity) {
        return tx.execute(status -> {
            entityManager.persist(entity);
            entityManager.flush();
            entityManager.refresh(entity);
            return entity;
        });
    }
}
