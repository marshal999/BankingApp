package ir.challange.bank.transaction.mapper;

import ir.challange.bank.account.AccountService;
import ir.challange.bank.transaction.dto.DebitRequest;
import ir.challange.bank.transaction.dto.TransferRequest;
import ir.challange.bank.transaction.TransactionEntity;
import ir.challange.bank.transaction.TransactionStatus;
import ir.challange.bank.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class TransferTransactionMapper implements TransactionMapper<TransferRequest>{

    private final AccountService accountService;

    @Override
    public TransactionEntity toEntity(TransferRequest request) {

        return TransactionEntity
                .builder()
                .idempotencyKey(request.idempotencyKey())
                .type(TransactionType.TRANSFER)
                .sourceAccount(accountService.findByAccountNumber(request.sourceAccountNumber()))
                .destinationAccount(accountService.findByAccountNumber(request.destinationAccountNumber()))
                .amount(BigDecimal.valueOf(request.amount()))
                .status(TransactionStatus.PENDING)
                .build();
    }

    @Override
    public Class<TransferRequest> getRequestType() {

        return TransferRequest.class;
    }

    @Override
    public boolean matches(TransferRequest request, TransactionEntity existing) {

        if (request.sourceAccountNumber().equals(existing.getSourceAccount().getAccountNumber()) &&
                request.destinationAccountNumber().equals(existing.getDestinationAccount().getAccountNumber()) &&
                BigDecimal.valueOf(request.amount()).compareTo(existing.getAmount()) == 0 &&
                request.idempotencyKey().equals(existing.getIdempotencyKey()))
            return true;
        return false;
    }
}
