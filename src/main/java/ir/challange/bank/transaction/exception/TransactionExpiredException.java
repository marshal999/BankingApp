package ir.challange.bank.transaction.exception;


public class TransactionExpiredException extends RuntimeException {

    public TransactionExpiredException(String idempotencyKey) {
        super(String.format("Transaction with idempotency key '%s' has expired.", idempotencyKey));
    }
}

