package inno.paymentservice.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "payments")
public class Payment extends BaseEntity {

    @Id
    private UUID id;

    @Indexed
    private UUID orderId;

    @Indexed
    private UUID userId;

    private PaymentStatus status;

    @Indexed
    private LocalDateTime timestamp;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal paymentAmount;

    private PaymentCurrency currency;

    private String stripePaymentIntentId;
}