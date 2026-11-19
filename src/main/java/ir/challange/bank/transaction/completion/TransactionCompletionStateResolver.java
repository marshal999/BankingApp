package ir.challange.bank.transaction.completion;

import ir.challange.bank.transaction.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Map;

import static ir.challange.bank.transaction.TransactionStatus.*;

@Component
public class TransactionCompletionStateResolver {

    private final Map<TransactionStatus, TransactionCompletionState> states;

    public TransactionCompletionStateResolver(
            CompletedState completedState,
            ExpiredState expiredState,
            PendingState pendingState,
            FailedState failedState) {

        this.states = Map.of(COMPLETED, completedState, EXPIRED, expiredState, PENDING, pendingState, FAILED, failedState);
    }

    public TransactionCompletionState resolve(TransactionStatus status) {

        TransactionCompletionState state = states.get(status);
        if (state == null)
            throw new IllegalArgumentException("Unsupported status: " + status);
        return state;
    }
}