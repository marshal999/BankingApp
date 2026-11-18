package ir.challange.bank.account.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String accountId) {
        super(String.format("There is no account with account number: %s", accountId));
    }
}
