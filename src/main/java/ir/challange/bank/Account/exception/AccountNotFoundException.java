package ir.challange.bank.Account.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String accountId) {
        super(String.format("There is no account with account number: %s", accountId));
    }
}
