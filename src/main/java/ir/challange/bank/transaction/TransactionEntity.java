package ir.challange.bank.transaction;

import ir.challange.bank.account.AccountEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "TRANSACTION")
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_", nullable = false, updatable = false)
    private UUID id;

    @NotBlank(message = "Idempotency Key is required")
    @Size(max = 36, message = "Idempotency key cannot exceed 36 characters")
    @Column(name = "IDEMPOTENCY_KEY_", nullable = false, unique = true, updatable = false, length = 36)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Transaction Type is required")
    @Column(name = "TYPE_", nullable = false, updatable = false)
    private TransactionType type;

    @NotNull(message = "Source account is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SOURCE_ACCOUNT_ID_", nullable = false, updatable = false)
    private AccountEntity sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DESTINATION_ACCOUNT_ID_", updatable = false)
    private AccountEntity destinationAccount;

    @NotNull(message = "Transaction amount is required")
    @DecimalMin(value = "0.0000", message = "amount cannot be negative")
    @Column(name = "AMOUNT_", nullable = false, updatable = false)
    private BigDecimal amount;

    @NotNull(message = "Timestamp is required")
    @Column(name = "TIMESTAMP_", nullable = false, updatable = false)
    private Instant timestamp;

    @PrePersist
    protected void onCreate() {
        timestamp = Instant.now();
    }

    @AssertTrue(message = "Destination account is required for TRANSFER transactions")
    public boolean isDestinationAccountValid() {

        return type != TransactionType.TRANSFER || destinationAccount != null;
    }
}










