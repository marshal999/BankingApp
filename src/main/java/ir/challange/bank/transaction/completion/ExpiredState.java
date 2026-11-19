package ir.challange.bank.transaction.completion;

import ir.challange.bank.transaction.exception.TransactionExpiredException;
import org.springframework.stereotype.Component;

@Component
public class ExpiredState implements TransactionCompletionState {

    @Override
    public void handle(TransactionCompletionContext context) {
        throw new TransactionExpiredException(context.transaction().getIdempotencyKey());
    }
}
