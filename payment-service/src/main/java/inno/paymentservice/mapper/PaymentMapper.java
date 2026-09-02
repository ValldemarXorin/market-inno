package inno.paymentservice.mapper;

import inno.paymentservice.dto.request.CreatePaymentRequest;
import inno.paymentservice.dto.response.PaymentResponse;
import inno.paymentservice.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Payment toEntity(CreatePaymentRequest request);

    PaymentResponse toResponse(Payment payment);
}