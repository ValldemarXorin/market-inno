package inno.orderservice.dao.specification;

import inno.orderservice.entity.Order;
import inno.orderservice.entity.OrderStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSpecificationTest {

    @Mock
    private Root<Order> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Test
    void shouldFilterByCreationDateRange() {
        LocalDateTime from = LocalDateTime.of(2026, Month.JANUARY, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, Month.JANUARY, 31, 23, 59);

        when(cb.conjunction()).thenReturn(mock(Predicate.class));
        when(cb.and(any(), any())).thenReturn(mock(Predicate.class));
        when(cb.greaterThanOrEqualTo(any(), any(LocalDateTime.class))).thenReturn(mock(Predicate.class));
        when(cb.lessThanOrEqualTo(any(), any(LocalDateTime.class))).thenReturn(mock(Predicate.class));

        OrderSpecification.createdBetween(from, to).toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(isNull(), eq(from));
        verify(cb).lessThanOrEqualTo(isNull(), eq(to));
    }

    @Test
    void shouldFilterByFromDateOnly() {
        LocalDateTime from = LocalDateTime.of(2026, Month.JANUARY, 1, 0, 0);

        when(cb.conjunction()).thenReturn(mock(Predicate.class));
        when(cb.and(any(), any())).thenReturn(mock(Predicate.class));
        when(cb.greaterThanOrEqualTo(any(), any(LocalDateTime.class))).thenReturn(mock(Predicate.class));

        OrderSpecification.createdBetween(from, null).toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(isNull(), eq(from));
    }

    @Test
    void shouldFilterByToDateOnly() {
        LocalDateTime to = LocalDateTime.of(2026, Month.JANUARY, 31, 23, 59);

        when(cb.conjunction()).thenReturn(mock(Predicate.class));
        when(cb.and(any(), any())).thenReturn(mock(Predicate.class));
        when(cb.lessThanOrEqualTo(any(), any(LocalDateTime.class))).thenReturn(mock(Predicate.class));

        OrderSpecification.createdBetween(null, to).toPredicate(root, query, cb);

        verify(cb).lessThanOrEqualTo(isNull(), eq(to));
    }

    @Test
    void shouldNotFilterWhenDateRangeIsEmpty() {
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        Predicate predicate = OrderSpecification.createdBetween(null, null).toPredicate(root, query, cb);

        assertNotNull(predicate);
        verify(cb).conjunction();
    }

    @Test
    void shouldFilterByStatuses() {
        Path<Object> statusPath = mock(Path.class);
        when(root.get("status")).thenReturn(statusPath);

        OrderSpecification.hasStatuses(List.of(OrderStatus.CREATED, OrderStatus.COMPLETED))
                .toPredicate(root, query, cb);

        verify(root).get("status");
        verify(statusPath).in(List.of(OrderStatus.CREATED, OrderStatus.COMPLETED));
    }

    @Test
    void shouldFilterByUserId() {
        UUID userId = UUID.randomUUID();

        when(cb.equal(any(), any(Object.class))).thenReturn(mock(Predicate.class));

        OrderSpecification.hasUserId(userId).toPredicate(root, query, cb);

        verify(cb).equal(isNull(), eq(userId));
    }

    @Test
    void shouldFilterOutDeletedOrders() {
        when(cb.equal(any(), any(Object.class))).thenReturn(mock(Predicate.class));

        OrderSpecification.notDeleted().toPredicate(root, query, cb);

        verify(cb).equal(isNull(), eq(false));
    }
}