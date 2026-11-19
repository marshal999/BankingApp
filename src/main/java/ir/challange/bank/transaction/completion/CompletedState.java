package ir.challange.bank.transaction.completion;

import org.springframework.stereotype.Component;

@Component
public class CompletedState implements TransactionCompletionState {

    @Override
    public void handle(TransactionCompletionContext context) {
        // do nothing
    }
}
