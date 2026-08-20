package inno.user_service.security.util;

import inno.user_service.dao.repository.PaymentCardRepository;
import inno.user_service.security.CurrentUser;
import inno.user_service.security.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ResourceSecurityService {

    private static final String FORBIDDEN_MESSAGE = "You do not have permission to access this resource";

    private final CurrentUser currentUser;
    private final PaymentCardRepository paymentCardRepository;

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

    public void requireAdminOrCardOwner(UUID cardId) {
        if (currentUser.isAdmin()) {
            return;
        }
        boolean isOwner = paymentCardRepository.findUserIdById(cardId)
                .map(ownerId -> ownerId.equals(currentUser.id()))
                .orElse(false);
        if (!isOwner) {
            throw new ForbiddenException(FORBIDDEN_MESSAGE);
        }
    }
}
