package ir.challange.bank.transaction.mapper;

import ir.challange.bank.account.AccountService;
import ir.challange.bank.transaction.dto.CreditRequest;
import ir.challange.bank.transaction.TransactionEntity;
import ir.challange.bank.transaction.TransactionStatus;
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
                .status(TransactionStatus.PENDING)
                .build();
    }

    @Override
    public Class<CreditRequest> getRequestType() {

        return CreditRequest.class;
    }

    @Override
    public boolean matches(CreditRequest request, TransactionEntity existing) {

        if (request.accountNumber().equals(existing.getSourceAccount().getAccountNumber()) &&
            BigDecimal.valueOf(request.amount()).compareTo(existing.getAmount()) == 0 &&
            request.idempotencyKey().equals(existing.getIdempotencyKey()))
            return true;
        return false;
    }
}
