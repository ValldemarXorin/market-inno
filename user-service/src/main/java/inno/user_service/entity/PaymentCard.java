package inno.user_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_cards")
public class PaymentCard extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank
    @Pattern(regexp = "\\d{13,19}", message = "Card number must contain 13 to 19 digits")
    @Column(nullable = false, length = 19)
    private String number;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String holder;

    @NotNull
    @FutureOrPresent
    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @NotNull
    @Column(nullable = false)
    private Boolean active = true;
}