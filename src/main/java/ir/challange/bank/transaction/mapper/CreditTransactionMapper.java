package ir.challange.bank.transaction.mapper;

import ir.challange.bank.account.AccountService;
import ir.challange.bank.account.dto.CreditRequest;
import ir.challange.bank.transaction.TransactionEntity;
import ir.challange.bank.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class CreditTransactionMapper implements TransactionMapper<CreditRequest> {

    private final AccountService accountService;

    @Override
    public TransactionEntity toEntity(CreditRequest request) {

        return TransactionEntity
                .builder()
                .idempotencyKey(request.idempotencyKey())
                .type(TransactionType.CREDIT)
                .sourceAccount(accountService.findByAccountNumber(request.accountNumber()))
                .amount(BigDecimal.valueOf(request.amount()))
                .build();
    }

    @Override
    public Class<CreditRequest> getRequestType() {

        return CreditRequest.class;
    }
}
