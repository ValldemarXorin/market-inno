package inno.paymentservice.dto.response;

import java.math.BigDecimal;

public record TotalResponse(
        BigDecimal total
) {
}