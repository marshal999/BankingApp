package ir.challange.bank.transaction;

import ir.challange.bank.transaction.dto.TransactionRequest;
import ir.challange.bank.transaction.mapper.TransactionMapperRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionClaimService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapperRegistry mapperRegistry;
    private final PendingTransactionInserter pendingTransactionInserter;

    /** Inserts PENDING with full request data, or no-ops if key already exists. */
    public void ensureExists(TransactionRequest request) {
        if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            return;
        }
        try {
            pendingTransactionInserter.insert(request);
        } catch (DataIntegrityViolationException ignored) {
            // lost the insert race — the other transaction created the row
        }
    }

    @Service
    @RequiredArgsConstructor
    static class PendingTransactionInserter {

        private final TransactionRepository transactionRepository;
        private final TransactionMapperRegistry mapperRegistry;

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void insert(TransactionRequest request) {
            if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
                return;
            }
            TransactionEntity entity = mapperRegistry.getMapper(request).toEntity(request);
            entity.setStatus(TransactionStatus.PENDING);
            transactionRepository.saveAndFlush(entity);
        }
    }
}
