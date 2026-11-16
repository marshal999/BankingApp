package ir.challange.bank.Account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TransferRequest (
        @NotBlank(message = "Source Account number is required")
        @Size(min = 15, max = 15, message = "Source account number must be exactly 15 characters")
        String sourceAccountNumber,

        @NotBlank(message = "Destination account number is required")
        @Size(min = 15, max = 15, message = "Destination account number must be exactly 15 characters")
        String destinationAccountNumber,

        @DecimalMin(value = "0.0000", message = "amount cannot be negative")
        long amount,

        String transactionId
){}
