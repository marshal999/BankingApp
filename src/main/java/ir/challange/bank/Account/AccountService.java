package ir.challange.bank.Account;

import ir.challange.bank.Account.dto.CreateAccountRequest;
import ir.challange.bank.Account.dto.CreditRequest;
import ir.challange.bank.Account.dto.DebitRequest;
import ir.challange.bank.Account.dto.TransferRequest;
import jakarta.validation.Valid;

public interface AccountService {

    AccountEntity createAccount(@Valid CreateAccountRequest request);

    void credit(CreditRequest request);

    void debit(DebitRequest request);

    void transfer(TransferRequest request);

}
