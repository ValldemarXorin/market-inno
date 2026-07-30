package inno.user_service.mapper;

import inno.user_service.dto.request.CreatePaymentCardRequest;
import inno.user_service.dto.response.PaymentCardResponse;
import inno.user_service.entity.PaymentCard;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentCardMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PaymentCard toEntity(CreatePaymentCardRequest request);

    @Mapping(target = "userId", source = "user.id")
    PaymentCardResponse toResponse(PaymentCard card);
}
