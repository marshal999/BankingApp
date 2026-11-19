package ir.challange.bank.transaction.exception;

public class SameSourceAndDestinationTransferException extends RuntimeException {
    public SameSourceAndDestinationTransferException() {
        super("Source and destination accounts must be different.");
    }
}
