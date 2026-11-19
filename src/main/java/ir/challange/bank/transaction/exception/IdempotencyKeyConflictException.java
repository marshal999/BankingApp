package ir.challange.bank.transaction.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String idempotencyKey) {
        super(String.format("Idempotency key = %s has already been used for a different request: ", idempotencyKey));
    }
}
