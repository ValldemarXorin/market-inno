package inno.user_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity{

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String username;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String surname;

    @NotNull
    @Past
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @NotBlank
    @Email
    @Size(max = 255)
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @NotNull
    @Column(nullable = false)
    private Boolean active = true;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentCard> paymentCards = new ArrayList<>();
}
