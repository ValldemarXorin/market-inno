package inno.orderservice.security.util;

import inno.orderservice.dao.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("resourceSecurity")
@RequiredArgsConstructor
public class ResourceSecurityService {

    private final OrderRepository orderRepository;

    public boolean isSelf(Authentication authentication, UUID resourceOwnerId) {
        return authentication.getPrincipal().equals(resourceOwnerId);
    }

    public boolean isOrderOwner(Authentication authentication, UUID orderId) {
        return orderRepository.findUserIdById(orderId)
                .map(ownerId -> ownerId.equals(authentication.getPrincipal()))
                .orElse(false);
    }
}