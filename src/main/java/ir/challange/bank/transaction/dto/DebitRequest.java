package ir.challange.bank.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DebitRequest (

        @NotBlank(message = "Account number is required")
        @Size(min = 15, max = 15, message = "Account number must be exactly 15 characters")
        String accountNumber,

        @Positive(message = "amount must be positive")
        long amount,

        @NotBlank(message = "Idempotency Key is required")
        @Size(max = 36, message = "Idempotency key cannot exceed 36 characters")
        String idempotencyKey

) implements TransactionRequest {}
