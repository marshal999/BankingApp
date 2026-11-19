package ir.challange.bank.transaction.completion;

public interface TransactionCompletionState {
    void handle(TransactionCompletionContext context);
}
