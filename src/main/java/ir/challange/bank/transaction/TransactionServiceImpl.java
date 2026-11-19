package ir.challange.bank.transaction;

import ir.challange.bank.account.AccountService;
import ir.challange.bank.account.exception.InsufficientFundsException;
import ir.challange.bank.transaction.completion.TransactionCompletionContext;
import ir.challange.bank.transaction.completion.TransactionCompletionStateResolver;
import ir.challange.bank.transaction.dto.CreditRequest;
import ir.challange.bank.transaction.dto.DebitRequest;
import ir.challange.bank.transaction.dto.TransferRequest;
import ir.challange.bank.transaction.exception.IdempotencyKeyConflictException;
import ir.challange.bank.transaction.exception.SameSourceAndDestinationTransferException;
import ir.challange.bank.transaction.mapper.TransactionMapperRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final TransactionClaimService transactionClaimService;
    private final TransactionCompletionStateResolver stateResolver;
    private final TransactionMapperRegistry transactionMapperRegistry;


    @Override
    @Transactional
    public void credit(@Valid CreditRequest request) {

        transactionClaimService.ensureExists(request);
        TransactionEntity tx = transactionRepository
                .findByIdempotencyKeyForUpdate(request.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("Missing idempotency row"));
        if  (!transactionMapperRegistry.getMapper(request).matches(request, tx))
            throw new IdempotencyKeyConflictException(request.idempotencyKey());

        TransactionCompletionContext context = new TransactionCompletionContext(
                tx,
                () -> accountService.credit(request.accountNumber(), BigDecimal.valueOf(request.amount())));
        stateResolver.resolve(tx.getStatus()).handle(context);
    }


    @Override
    @Transactional(noRollbackFor = InsufficientFundsException.class)
    public void debit(@Valid DebitRequest request) {

        transactionClaimService.ensureExists(request);
        TransactionEntity tx = transactionRepository
                .findByIdempotencyKeyForUpdate(request.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("Missing idempotency row"));
        if  (!transactionMapperRegistry.getMapper(request).matches(request, tx))
            throw new IdempotencyKeyConflictException(request.idempotencyKey());

        TransactionCompletionContext context = new TransactionCompletionContext(
                tx,
                () -> accountService.debit(request.accountNumber(), BigDecimal.valueOf(request.amount())));

        try {
            stateResolver.resolve(tx.getStatus()).handle(context);
        } catch (InsufficientFundsException ex) {
            tx.setStatus(TransactionStatus.FAILED);
            throw ex;
        }
    }

    @Override
    @Transactional(noRollbackFor = InsufficientFundsException.class)
    public void transfer(@Valid TransferRequest request) {

        if (request.destinationAccountNumber().equals(request.sourceAccountNumber()))
            throw new SameSourceAndDestinationTransferException();
        transactionClaimService.ensureExists(request);
        TransactionEntity tx = transactionRepository
                .findByIdempotencyKeyForUpdate(request.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("Missing idempotency row"));
        if  (!transactionMapperRegistry.getMapper(request).matches(request, tx))
            throw new IdempotencyKeyConflictException(request.idempotencyKey());

        TransactionCompletionContext context = new TransactionCompletionContext(
                tx,
                () -> {
                    accountService.transfer(request.sourceAccountNumber(),
                            request.destinationAccountNumber(),
                            BigDecimal.valueOf(request.amount()));
                });
        try {
            stateResolver.resolve(tx.getStatus()).handle(context);
        } catch (InsufficientFundsException ex) {
            tx.setStatus(TransactionStatus.FAILED);
            throw ex;
        }
    }



}
