package ir.challange.bank.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DebitRequest (

        @NotBlank(message = "Account number is required")
        @Size(min = 15, max = 15, message = "Account number must be exactly 15 characters")
        String accountNumber,

        @DecimalMin(value = "0.0000", message = "amount cannot be negative")
        long amount,

        @NotBlank(message = "Idempotency Key is required")
        @Size(max = 36, message = "Idempotency key cannot exceed 36 characters")
        String idempotencyKey

) implements TransactionRequest {}
