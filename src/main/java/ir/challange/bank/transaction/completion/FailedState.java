package ir.challange.bank.transaction.completion;

import ir.challange.bank.transaction.exception.TransactionAlreadyFailedException;
import org.springframework.stereotype.Component;

@Component
public class FailedState implements TransactionCompletionState{

    @Override
    public void handle(TransactionCompletionContext context) {
        throw new TransactionAlreadyFailedException(context.transaction().getIdempotencyKey());
    }
}
