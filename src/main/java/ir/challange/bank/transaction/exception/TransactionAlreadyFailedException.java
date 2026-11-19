package ir.challange.bank.transaction.exception;

public class TransactionAlreadyFailedException extends RuntimeException {

    public TransactionAlreadyFailedException(String idempotencyKey) {
        super(String.format("Transaction with idempotency key '%s' has already failed.", idempotencyKey));
    }
}
