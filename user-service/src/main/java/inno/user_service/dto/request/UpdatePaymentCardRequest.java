package inno.user_service.dto.request;

import inno.user_service.util.MaskingUtil;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record UpdatePaymentCardRequest(
        @NotBlank(message = "Card number must not be blank")
        @Pattern(regexp = "\\d{13,19}", message = "Card number must contain 13 to 19 digits")
        String number,

        @NotBlank(message = "Holder name must not be blank")
        @Size(max = 100, message = "Holder name must not exceed 100 characters")
        String holder,

        @NotNull(message = "Expiration date must not be null")
        @FutureOrPresent(message = "Expiration date must not be in the past")
        LocalDate expirationDate
) {
        @Override
        public String toString() {
                return "CreatePaymentCardRequest[number=" + MaskingUtil.maskCardNumber(number)
                        + ", holder=" + holder
                        + ", expirationDate=" + expirationDate
                        + "]";
        }
}