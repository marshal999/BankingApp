package ir.challange.bank.transaction.mapper;

import ir.challange.bank.account.AccountService;
import ir.challange.bank.account.dto.TransferRequest;
import ir.challange.bank.transaction.TransactionEntity;
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
                .build();
    }

    @Override
    public Class<TransferRequest> getRequestType() {

        return TransferRequest.class;
    }
}
