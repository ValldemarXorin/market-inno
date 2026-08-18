package inno.orderservice.security.util;

import inno.orderservice.dao.repository.OrderRepository;
import inno.orderservice.security.CurrentUser;
import inno.orderservice.security.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ResourceSecurityService {

    private static final String FORBIDDEN_MESSAGE = "You do not have permission to access this resource";

    private final CurrentUser currentUser;
    private final OrderRepository orderRepository;

    public void requireAdmin() {
        if (!currentUser.isAdmin()) {
            throw new ForbiddenException(FORBIDDEN_MESSAGE);
        }
    }

    public void requireAdminOrSelf(UUID resourceOwnerId) {
        if (currentUser.isAdmin() || currentUser.id().equals(resourceOwnerId)) {
            return;
        }
        throw new ForbiddenException(FORBIDDEN_MESSAGE);
    }

    public void requireAdminOrOrderOwner(UUID orderId) {
        if (currentUser.isAdmin()) {
            return;
        }
        boolean isOwner = orderRepository.findUserIdById(orderId)
                .map(ownerId -> ownerId.equals(currentUser.id()))
                .orElse(false);
        if (!isOwner) {
            throw new ForbiddenException(FORBIDDEN_MESSAGE);
        }
    }
}