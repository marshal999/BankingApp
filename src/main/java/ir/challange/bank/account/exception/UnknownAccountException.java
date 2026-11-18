package ir.challange.bank.account.exception;

public class UnknownAccountException extends RuntimeException {
    public UnknownAccountException(String accountId) {
        super(String.format("There is no account with account number: %s", accountId));
    }
}
