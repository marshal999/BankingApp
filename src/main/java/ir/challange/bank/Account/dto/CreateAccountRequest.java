package ir.challange.bank.Account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(

        @NotBlank(message = "Account number is required")
        @Size(min = 15, max = 15, message = "Account number must be exactly 15 characters")
        String accountNumber,

        @NotBlank(message = "Account owner social security number is required")
        @Pattern(regexp = "[0-9]{10}", message = "SSN must contain exactly 10 digits")
        String ownerSsn
) {}
