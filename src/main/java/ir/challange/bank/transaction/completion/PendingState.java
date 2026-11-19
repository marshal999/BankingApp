package ir.challange.bank.transaction.completion;

import ir.challange.bank.transaction.TransactionStatus;
import org.springframework.stereotype.Component;

@Component
public class PendingState implements TransactionCompletionState {

    @Override
    public void handle(TransactionCompletionContext context) {

        // There may be a brief window during which a transaction has exceeded its expiration time
        // but its status has not yet been updated. In this case, we still process the transaction
        // instead of treating it as expired.
        context.moneyOperation().run();
        context.transaction().setStatus(TransactionStatus.COMPLETED);
    }
}
