package ir.challange.bank.transaction;

import ir.challange.bank.account.AccountService;
import ir.challange.bank.account.dto.CreditRequest;
import ir.challange.bank.account.dto.DebitRequest;
import ir.challange.bank.account.dto.TransferRequest;
import ir.challange.bank.transaction.mapper.TransactionMapper;
import ir.challange.bank.transaction.mapper.TransactionMapperRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final TransactionMapperRegistry mapperRegistry;

    private void logRequest(RecordableTransactionRequest request) {

        TransactionMapper<RecordableTransactionRequest> mapper = mapperRegistry.getMapper(request);
        TransactionEntity transaction = mapper.toEntity(request);
        transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public void credit(CreditRequest request) {

        accountService.credit(request.accountNumber(), BigDecimal.valueOf(request.amount()));
        logRequest(request);
    }

    @Override
    @Transactional
    public void debit(DebitRequest request) {

        accountService.debit(request.accountNumber(), BigDecimal.valueOf(request.amount()));
        logRequest(request);
    }

    @Override
    @Transactional
    public void transfer(TransferRequest request) {

        accountService.debit(request.sourceAccountNumber(), BigDecimal.valueOf(request.amount()));
        accountService.credit(request.destinationAccountNumber(), BigDecimal.valueOf(request.amount()));
        logRequest(request);
    }



}
