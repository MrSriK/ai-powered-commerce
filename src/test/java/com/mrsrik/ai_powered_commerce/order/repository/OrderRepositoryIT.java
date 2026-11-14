package com.mrsrik.ai_powered_commerce.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.entity.OrderItem;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.support.AbstractPostgresIT;
import com.mrsrik.ai_powered_commerce.user.entity.User;

class OrderRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Test
    @DisplayName("findByUserIdAndDeletedAtIsNull returns orders for user and excludes soft-deleted orders")
    void findByUserIdAndDeletedAtIsNullExcludesDeletedAndOtherUsers() {
        User user1 = testData.user();
        User user2 = testData.user();

        Order order1 = testData.order(user1.getId(), OrderStatus.PENDING);
        Order order2 = testData.order(user1.getId(), OrderStatus.CONFIRMED);
        testData.order(user1.getId(), OrderStatus.PENDING, b -> b.deletedAt(Instant.now()));
        testData.order(user2.getId(), OrderStatus.PENDING);

        Page<Order> page = orderRepository.findByUserIdAndDeletedAtIsNull(user1.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(Order::getId)
                .containsExactlyInAnyOrder(order1.getId(), order2.getId());
    }

    @Test
    @DisplayName("findByIdAndUserIdAndDeletedAtIsNull enforces ownership and excludes soft-deleted orders")
    void findByIdAndUserIdAndDeletedAtIsNullOwnerScoped() {
        User owner = testData.user();
        User otherUser = testData.user();

        Order order = testData.order(owner.getId(), OrderStatus.PENDING);
        Order deletedOrder = testData.order(owner.getId(), OrderStatus.PENDING, b -> b.deletedAt(Instant.now()));

        // Found when matching owner and not deleted
        Optional<Order> found = orderRepository.findByIdAndUserIdAndDeletedAtIsNull(order.getId(), owner.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(order.getId());

        // Not found when requested by another user
        Optional<Order> notFoundOtherUser = orderRepository.findByIdAndUserIdAndDeletedAtIsNull(order.getId(), otherUser.getId());
        assertThat(notFoundOtherUser).isEmpty();

        // Not found when soft-deleted
        Optional<Order> notFoundDeleted = orderRepository.findByIdAndUserIdAndDeletedAtIsNull(deletedOrder.getId(), owner.getId());
        assertThat(notFoundDeleted).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("findForUpdateById acquires pessimistic lock and excludes soft-deleted orders")
    void findForUpdateByIdPessimisticLock() {
        User user = testData.user();
        Order order = testData.order(user.getId(), OrderStatus.PENDING);
        Order deletedOrder = testData.order(user.getId(), OrderStatus.PENDING, b -> b.deletedAt(Instant.now()));

        Optional<Order> locked = orderRepository.findForUpdateById(order.getId());
        assertThat(locked).isPresent();
        assertThat(locked.get().getId()).isEqualTo(order.getId());

        Optional<Order> lockedDeleted = orderRepository.findForUpdateById(deletedOrder.getId());
        assertThat(lockedDeleted).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("findForUpdateByIdAndUserId acquires owner-scoped pessimistic lock")
    void findForUpdateByIdAndUserIdOwnerScoped() {
        User owner = testData.user();
        User other = testData.user();
        Order order = testData.order(owner.getId(), OrderStatus.PENDING);

        Optional<Order> locked = orderRepository.findForUpdateByIdAndUserId(order.getId(), owner.getId());
        assertThat(locked).isPresent();

        Optional<Order> lockedOther = orderRepository.findForUpdateByIdAndUserId(order.getId(), other.getId());
        assertThat(lockedOther).isEmpty();
    }

    @Test
    @DisplayName("findIdsByStatus returns orders matching status, ordered by createdAt ASC and id ASC")
    void findIdsByStatusOrderingAndBatching() {
        User user = testData.user();

        Order o1 = testData.order(user.getId(), OrderStatus.PENDING);
        Order o2 = testData.order(user.getId(), OrderStatus.PENDING);
        testData.order(user.getId(), OrderStatus.CONFIRMED);
        testData.order(user.getId(), OrderStatus.PENDING, b -> b.deletedAt(Instant.now()));

        List<UUID> pendingIds = orderRepository.findIdsByStatus(OrderStatus.PENDING, PageRequest.of(0, 10));
        assertThat(pendingIds).containsExactly(o1.getId(), o2.getId());

        // Batch limit test
        List<UUID> limitedIds = orderRepository.findIdsByStatus(OrderStatus.PENDING, PageRequest.of(0, 1));
        assertThat(limitedIds).hasSize(1);
        assertThat(limitedIds.getFirst()).isEqualTo(o1.getId());
    }

    @Test
    @DisplayName("findDeliverableIds returns SHIPPED orders with shippedAt <= cutoff, ordered by shippedAt ASC, id ASC")
    void findDeliverableIdsCutoffAndOrdering() {
        User user = testData.user();
        Instant now = Instant.now();
        Instant cutoff = now.minus(5, ChronoUnit.MINUTES);

        // Eligible: shipped 10 mins ago (shippedAt <= cutoff)
        Order eligible1 = testData.order(user.getId(), OrderStatus.SHIPPED, b -> b.shippedAt(now.minus(10, ChronoUnit.MINUTES)));
        // Eligible: shipped 6 mins ago
        Order eligible2 = testData.order(user.getId(), OrderStatus.SHIPPED, b -> b.shippedAt(now.minus(6, ChronoUnit.MINUTES)));
        // Ineligible: shipped 1 min ago (shippedAt > cutoff)
        testData.order(user.getId(), OrderStatus.SHIPPED, b -> b.shippedAt(now.minus(1, ChronoUnit.MINUTES)));
        // Ineligible: not SHIPPED (CONFIRMED)
        testData.order(user.getId(), OrderStatus.CONFIRMED);
        // Ineligible: soft-deleted
        testData.order(user.getId(), OrderStatus.SHIPPED, b -> b
                .shippedAt(now.minus(10, ChronoUnit.MINUTES))
                .deletedAt(now));

        List<UUID> deliverableIds = orderRepository.findDeliverableIds(cutoff, PageRequest.of(0, 10));
        assertThat(deliverableIds).containsExactly(eligible1.getId(), eligible2.getId());
    }

    @Test
    @DisplayName("findByOrderIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc returns ordered items and excludes soft-deleted items")
    void orderItemsOrderedByCreatedAtAscIdAsc() {
        User user = testData.user();
        Order order = testData.order(user.getId(), OrderStatus.PENDING);
        Order otherOrder = testData.order(user.getId(), OrderStatus.PENDING);

        Category category = testData.category();
        Product product = testData.product(category.getId());

        OrderItem item1 = testData.orderItem(order.getId(), product.getId(), 2);
        OrderItem item2 = testData.orderItem(order.getId(), product.getId(), 1);
        testData.orderItem(order.getId(), product.getId(), 3, b -> b.deletedAt(Instant.now()));
        testData.orderItem(otherOrder.getId(), product.getId(), 5);

        List<OrderItem> items = orderItemRepository.findByOrderIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(order.getId());

        assertThat(items)
                .extracting(OrderItem::getId)
                .containsExactly(item1.getId(), item2.getId());
    }
}

