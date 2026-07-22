package inno.user_service.dao.specification;

import inno.user_service.entity.PaymentCard;
import inno.user_service.entity.User;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

public final class PaymentCardSpecification {

    private PaymentCardSpecification() {
    }

    public static Specification<PaymentCard> ownerHasName(String name) {
        return (root, query, cb) -> {
            if (name == null || name.isBlank()) {
                return cb.conjunction();
            }
            Join<PaymentCard, User> owner = root.join("user");
            return cb.like(cb.lower(owner.get("name")), "%" + name.toLowerCase() + "%");
        };
    }

    public static Specification<PaymentCard> ownerHasSurname(String surname) {
        return (root, query, cb) -> {
            if (surname == null || surname.isBlank()) {
                return cb.conjunction();
            }
            Join<PaymentCard, User> owner = root.join("user");
            return cb.like(cb.lower(owner.get("surname")), "%" + surname.toLowerCase() + "%");
        };
    }

    public static Specification<PaymentCard> filterByOwner(String name, String surname) {
        return Specification.where(ownerHasName(name)).and(ownerHasSurname(surname));
    }
}
