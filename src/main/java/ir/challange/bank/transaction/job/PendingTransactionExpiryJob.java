package ir.challange.bank.transaction.job;

import ir.challange.bank.transaction.TransactionProperties;
import ir.challange.bank.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingTransactionExpiryJob {

    private final TransactionProperties transactionProperties;
    private final TransactionRepository transactionRepository;

    @Scheduled(fixedDelayString = "${transaction.expire-pending-fixed-delay}")
    @Transactional
    public void expireStalePending() {
        Instant cutoff = Instant.now().minus(transactionProperties.pendingTtl());
        int updated = transactionRepository.expirePendingOlderThan(cutoff);

        log.info("Expired {} pending transactions", updated);
    }
}
