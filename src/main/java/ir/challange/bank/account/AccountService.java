package ir.challange.bank.account;

import ir.challange.bank.account.dto.CreateAccountRequest;
import jakarta.validation.Valid;

import java.math.BigDecimal;

public interface AccountService {

    AccountEntity createAccount(@Valid CreateAccountRequest request);

    AccountEntity findByAccountNumber(String accountNumber);

    void credit(String accountNumber, BigDecimal amount);

    void debit(String accountNumber, BigDecimal amount);

}
