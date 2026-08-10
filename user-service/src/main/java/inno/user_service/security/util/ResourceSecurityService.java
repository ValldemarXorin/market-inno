package inno.user_service.security.util;

import inno.user_service.dao.repository.PaymentCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("resourceSecurity")
@RequiredArgsConstructor
public class ResourceSecurityService {

    private final PaymentCardRepository paymentCardRepository;

    public boolean isSelf(Authentication authentication, UUID resourceOwnerId) {
        return authentication.getPrincipal().equals(resourceOwnerId);
    }

    public boolean isCardOwner(Authentication authentication, UUID cardId) {
        return paymentCardRepository.findUserIdById(cardId)
                .map(ownerId -> ownerId.equals(authentication.getPrincipal()))
                .orElse(false);
    }
}
