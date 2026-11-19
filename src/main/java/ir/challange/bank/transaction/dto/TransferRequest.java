package ir.challange.bank.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TransferRequest (
        @NotBlank(message = "Source Account number is required")
        @Size(min = 15, max = 15, message = "Source account number must be exactly 15 characters")
        String sourceAccountNumber,

        @NotBlank(message = "Destination account number is required")
        @Size(min = 15, max = 15, message = "Destination account number must be exactly 15 characters")
        String destinationAccountNumber,

        @Positive(message = "amount must be positive")
        long amount,

        @NotBlank(message = "Idempotency Key is required")
        @Size(max = 36, message = "Idempotency key cannot exceed 36 characters")
        String idempotencyKey
) implements TransactionRequest {}
