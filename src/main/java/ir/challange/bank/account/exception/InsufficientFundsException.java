package ir.challange.bank.account.exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String accountId) {
        super(String.format("Insufficient funds in account: %s", accountId));
    }
}
