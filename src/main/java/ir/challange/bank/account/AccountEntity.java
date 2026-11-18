package ir.challange.bank.account;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Setter
@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ACCOUNT")
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_", nullable = false, updatable = false)
    private UUID id;

    @NotBlank(message = "Account number is required")
    @Size(min = 15, max = 15, message = "Account number must be exactly 15 characters")
    @Column(name = "ACCOUNT_NUMBER_", nullable = false, unique = true, length = 15)
    private String accountNumber;

    @NotBlank(message = "Account owner social security number is required")
    @Pattern(regexp = "[0-9]{10}", message = "SSN must contain exactly 10 digits")
    @Column(name = "OWNER_SSN", nullable = false, length = 10)
    private String ownerSsn;

    @NotNull(message = "Account balance is required")
    @DecimalMin(value = "0.0000", message = "Balance cannot be negative")
    @Column(name = "BALANCE_", nullable = false)
    private BigDecimal balance;

}
