package ir.challange.bank.transaction.job;


import ir.challange.bank.account.AccountEntity;
import ir.challange.bank.account.AccountService;
import ir.challange.bank.account.dto.CreateAccountRequest;
import ir.challange.bank.transaction.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PendingTransactionExpiryJobIntegrationTest {

    @Autowired
    private PendingTransactionExpiryJob expiryJob;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionProperties transactionProperties;

    @Autowired
    private AccountService accountService;


    private TransactionEntity createPendingTransaction(AccountEntity account) {

        return TransactionEntity.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .type(TransactionType.CREDIT)
                .status(TransactionStatus.PENDING)
                .amount(BigDecimal.valueOf(100))
                .sourceAccount(account)
                .build();
    }

    @Test
    void expireStalePending_oldPendingTransactions_statusBecomesTimeout() {

        Instant now = Instant.now();
        Instant cutoff = now.minus(transactionProperties.pendingTtl());
        AccountEntity account = accountService.createAccount(new CreateAccountRequest("999456789012346", "0012345678"));
        TransactionEntity oldTransaction = createPendingTransaction(account);
        TransactionEntity recentTransaction = createPendingTransaction(account);
        transactionRepository.saveAllAndFlush(List.of(oldTransaction, recentTransaction));
        oldTransaction.setTimestamp(cutoff.minusSeconds(transactionProperties.pendingTtl().toSeconds()));
        transactionRepository.saveAndFlush(oldTransaction);

        expiryJob.expireStalePending();

        TransactionEntity expired = transactionRepository.findById(oldTransaction.getId()).orElseThrow();
        TransactionEntity recent = transactionRepository.findById(recentTransaction.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(TransactionStatus.EXPIRED);
        assertThat(recent.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }
}
