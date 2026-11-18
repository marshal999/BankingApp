package ir.challange.bank.transaction.mapper;

import ir.challange.bank.account.AccountService;
import ir.challange.bank.account.dto.DebitRequest;
import ir.challange.bank.transaction.TransactionEntity;
import ir.challange.bank.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DebitTransactionMapper implements TransactionMapper<DebitRequest> {

    private final AccountService accountService;

    @Override
    public TransactionEntity toEntity(DebitRequest request) {

        return TransactionEntity
                .builder()
                .idempotencyKey(request.idempotencyKey())
                .type(TransactionType.DEBIT)
                .sourceAccount(accountService.findByAccountNumber(request.accountNumber()))
                .amount(BigDecimal.valueOf(request.amount()))
                .build();
    }

    @Override
    public Class<DebitRequest> getRequestType() {

        return DebitRequest.class;
    }
}
