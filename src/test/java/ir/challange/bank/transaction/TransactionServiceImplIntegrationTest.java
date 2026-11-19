package ir.challange.bank.transaction;

import ir.challange.bank.account.AccountEntity;
import ir.challange.bank.account.AccountService;
import ir.challange.bank.account.dto.CreateAccountRequest;
import ir.challange.bank.transaction.dto.CreditRequest;
import ir.challange.bank.transaction.dto.DebitRequest;
import ir.challange.bank.transaction.dto.TransferRequest;
import ir.challange.bank.account.exception.InsufficientFundsException;
import ir.challange.bank.account.exception.UnknownAccountException;
import ir.challange.bank.transaction.exception.IdempotencyKeyConflictException;
import ir.challange.bank.transaction.exception.SameSourceAndDestinationTransferException;
import ir.challange.bank.transaction.exception.TransactionAlreadyFailedException;
import ir.challange.bank.transaction.exception.TransactionExpiredException;
import ir.challange.bank.transaction.mapper.TransactionMapperRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class TransactionServiceImplIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @MockitoSpyBean
    private AccountService accountService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionMapperRegistry transactionMapperRegistry;

    @Autowired
    private Validator validator;

    // region Credit tests

    @Test
    void credit_happyPath_completed() {

        String accountNumber = "888456789012345";
        String idempotencyKey = "credit-atomic-happy-001";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        CreditRequest request = new CreditRequest(accountNumber, 100, idempotencyKey);
        long transactionCountBefore = transactionRepository.count();

        transactionService.credit(request);

        AccountEntity account = accountService.findByAccountNumber(accountNumber);
        assertThat(account.getBalance()).isEqualByComparingTo("100");
        TransactionEntity transaction = transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transactionMapperRegistry.getMapper(request).matches(request, transaction));
    }

    @Test
    void credit_accountDoesNotExist_noTransactionIsRecorded() {

        String accountNumber = "884456789012345";
        String idempotencyKey = "credit-unknown-account-001";
        long transactionCountBefore = transactionRepository.count();

        assertThatThrownBy(() ->
                transactionService.credit(new CreditRequest(accountNumber, 100, idempotencyKey)))
                .isInstanceOf(UnknownAccountException.class);
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void credit_accountCreditFails_remainsPending() {

        String accountNumber = "888456789012347";
        String idempotencyKey = "credit-atomic-balance-fail-001";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        long transactionCountBefore = transactionRepository.count();
        CreditRequest request = new CreditRequest(accountNumber, 100, idempotencyKey);

        doThrow(new RuntimeException("unexpected balance update failure"))
                .when(accountService)
                .credit(any(), any());

        assertThatThrownBy(() ->
                transactionService.credit(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unexpected balance update failure");
        AccountEntity account = accountService.findByAccountNumber(accountNumber);
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        TransactionEntity transaction = transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transactionMapperRegistry.getMapper(request).matches(request, transaction));
    }

    @Test
    void credit_concurrentRequestsWithSameIdempotencyKey_executesOnlyOnce() throws Exception {

        int concurrentRequests = 5;
        AccountEntity account = accountService.createAccount(
                new CreateAccountRequest("999456789012348", "0012345678"));
        String idempotencyKey = "concurrent-credit-test-001";
        CreditRequest request = new CreditRequest(
                account.getAccountNumber(),
                100,
                idempotencyKey
        );
        BigDecimal initialBalance = account.getBalance();
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<CompletableFuture<Void>> futures = IntStream
                    .range(0, concurrentRequests)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        ready.countDown();
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                        transactionService.credit(request);
                    }, executor))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            ).get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        AccountEntity updatedAccount = accountService
                .findByAccountNumber(account.getAccountNumber());
        assertThat(updatedAccount.getBalance())
                .isEqualByComparingTo(initialBalance.add(BigDecimal.valueOf(100)));
        TransactionEntity transaction = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow();
        assertThat(transaction.getStatus())
                .isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void credit_sameIdempotencyKeyWithDifferentPayload_throwsIdempotencyKeyConflictException() {

        AccountEntity account = accountService.createAccount(
                new CreateAccountRequest("999456789012352", "0012345678"));
        String idempotencyKey = "credit-conflict-test-001";
        CreditRequest firstRequest = new CreditRequest(account.getAccountNumber(), 100, idempotencyKey);
        CreditRequest secondRequest = new CreditRequest(account.getAccountNumber(), 200, idempotencyKey);
        BigDecimal initialBalance = account.getBalance();

        transactionService.credit(firstRequest);

        assertThatThrownBy(() -> transactionService.credit(secondRequest))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        AccountEntity updatedAccount = accountService.findByAccountNumber(account.getAccountNumber());
        assertThat(updatedAccount.getBalance()).isEqualByComparingTo(initialBalance.add(BigDecimal.valueOf(100)));
        TransactionEntity transaction = transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transactionMapperRegistry.getMapper(firstRequest).matches(firstRequest, transaction));
    }

    @Test
    void credit_expiredTransaction_throwsTransactionExpiredException() {

        String accountNumber = "888456901782345";
        String idempotencyKey = "credit-expired-test-001";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        CreditRequest request = new CreditRequest(accountNumber, 100, idempotencyKey);
        TransactionEntity transaction = transactionMapperRegistry.getMapper(request).toEntity(request);
        transaction.setStatus(TransactionStatus.EXPIRED);
        transactionRepository.saveAndFlush(transaction);
        long transactionCountBefore = transactionRepository.count();

        assertThatThrownBy(() ->
                transactionService.credit(request)).isInstanceOf(TransactionExpiredException.class);
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void credit_failedTransaction_throwsTransactionAlreadyFailed() {

        String accountNumber = "888478901562345";
        String idempotencyKey = "credit-failed-test-001";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        CreditRequest request = new CreditRequest(accountNumber, 100, idempotencyKey);
        TransactionEntity transaction = transactionMapperRegistry.getMapper(request).toEntity(request);
        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.saveAndFlush(transaction);
        long transactionCountBefore = transactionRepository.count();

        assertThatThrownBy(() ->
                transactionService.credit(request)).isInstanceOf(TransactionAlreadyFailedException.class);
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void credit_concurrentRequests_bothSucceed() throws Exception {

        String accountNumber = "888456989012345";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<CompletableFuture<Boolean>> futures = IntStream.range(0, 2)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        ready.countDown();

                        try {
                            start.await();

                            transactionService.credit(
                                    new CreditRequest(accountNumber, 80, "credit-concurrency-" + i));

                            return true;

                        } catch (ObjectOptimisticLockingFailureException e) {
                            return false;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }, executor))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(15, TimeUnit.SECONDS);
            assertThat(futures.stream().filter(CompletableFuture::join).count()).isEqualTo(2);
            assertThat(accountService.findByAccountNumber(accountNumber).getBalance())
                    .isEqualByComparingTo("160");

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -100})
    void credit_amountMustBePositive(long amount) {
        CreditRequest request = new CreditRequest("888456789012345", amount, "credit-validation-test-001");

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("amount must be positive");
    }

    // endregion

    // region Debit tests

    @Test
    void debit_happyPath_completed() {

        String accountNumber = "887456789012345";
        String idempotencyKey = "debit-atomic-happy-001";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        accountService.credit(accountNumber, BigDecimal.valueOf(100));
        DebitRequest request = new DebitRequest(accountNumber, 40, idempotencyKey);
        long transactionCountBefore = transactionRepository.count();

        transactionService.debit(request);

        AccountEntity account = accountService.findByAccountNumber(accountNumber);
        assertThat(account.getBalance()).isEqualByComparingTo("60");
        TransactionEntity transaction = transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transactionMapperRegistry.getMapper(request).matches(request, transaction));
    }

    @Test
    void debit_accountDoesNotExist_noTransactionIsRecorded() {

        String accountNumber = "884456789012346";
        String idempotencyKey = "debit-unknown-account-001";
        long transactionCountBefore = transactionRepository.count();

        assertThatThrownBy(() ->
                transactionService.debit(new DebitRequest(accountNumber, 40, idempotencyKey)))
                .isInstanceOf(UnknownAccountException.class);
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void debit_accountDebitFails_remainsPending() {

        String accountNumber = "887456789012346";
        String idempotencyKey = "debit-atomic-balance-fail-001";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        accountService.credit(accountNumber, BigDecimal.valueOf(100));
        long transactionCountBefore = transactionRepository.count();
        DebitRequest request = new DebitRequest(accountNumber, 40, idempotencyKey);

        doThrow(new RuntimeException("unexpected balance update failure"))
                .when(accountService)
                .debit(any(), any());

        assertThatThrownBy(() ->
                transactionService.debit(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unexpected balance update failure");
        AccountEntity account = accountService.findByAccountNumber(accountNumber);
        assertThat(account.getBalance()).isEqualByComparingTo("100");
        TransactionEntity transaction = transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transactionMapperRegistry.getMapper(request).matches(request, transaction));
    }

    @Test
    void debit_balanceIsInsufficient_transactionIsMarkedAsFailed() {

        String accountNumber = "999456789012345";
        String idempotencyKey = "it-debit-insufficient-funds-001";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        long transactionCountBefore = transactionRepository.count();
        DebitRequest request = new DebitRequest(accountNumber, 1, idempotencyKey);

        assertThatThrownBy(() -> transactionService.debit(request)).isInstanceOf(InsufficientFundsException.class);
        AccountEntity source = accountService.findByAccountNumber(accountNumber);
        assertThat(source.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        TransactionEntity transaction = transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transactionMapperRegistry.getMapper(request).matches(request, transaction));
    }

    @Test
    void debit_concurrentRequestsWithSameIdempotencyKey_executesOnlyOnce() throws Exception {

        int concurrentRequests = 5;
        AccountEntity account = accountService.createAccount(
                new CreateAccountRequest("999456789012349", "0012345678"));
        accountService.credit(account.getAccountNumber(), BigDecimal.valueOf(1000));
        String idempotencyKey = "concurrent-debit-test-001";
        DebitRequest request = new DebitRequest(
                account.getAccountNumber(),
                100,
                idempotencyKey
        );
        BigDecimal initialBalance = accountService
                .findByAccountNumber(account.getAccountNumber())
                .getBalance();
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<CompletableFuture<Void>> futures = IntStream
                    .range(0, concurrentRequests)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        ready.countDown();
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                        transactionService.debit(request);
                    }, executor))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            ).get(15, TimeUnit.SECONDS);

        } finally {
            executor.shutdownNow();
        }

        AccountEntity updatedAccount = accountService
                .findByAccountNumber(account.getAccountNumber());
        assertThat(updatedAccount.getBalance())
                .isEqualByComparingTo(initialBalance.subtract(BigDecimal.valueOf(100)));
        TransactionEntity transaction = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow();
        assertThat(transaction.getStatus())
                .isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void debit_sameIdempotencyKeyWithDifferentPayload_throwsIdempotencyKeyConflictException() {

        AccountEntity account = accountService.createAccount(
                new CreateAccountRequest("999456789012353", "0012345678"));
        accountService.credit(account.getAccountNumber(), BigDecimal.valueOf(1000));
        String idempotencyKey = "debit-conflict-test-001";
        DebitRequest firstRequest = new DebitRequest(account.getAccountNumber(), 100, idempotencyKey);
        DebitRequest secondRequest = new DebitRequest(account.getAccountNumber(), 200, idempotencyKey);
        BigDecimal initialBalance = accountService.findByAccountNumber(account.getAccountNumber()).getBalance();

        transactionService.debit(firstRequest);

        assertThatThrownBy(() -> transactionService.debit(secondRequest))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        AccountEntity updatedAccount = accountService.findByAccountNumber(account.getAccountNumber());
        assertThat(updatedAccount.getBalance()).isEqualByComparingTo(initialBalance.subtract(BigDecimal.valueOf(100)));
        TransactionEntity transaction = transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transactionMapperRegistry.getMapper(firstRequest).matches(firstRequest, transaction)).isTrue();
    }

    @Test
    void debit_expiredTransaction_throwsTransactionExpiredException() {

        String accountNumber = "888434589012345";
        String idempotencyKey = "debit-expired-test-001";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        DebitRequest request = new DebitRequest(accountNumber, 100, idempotencyKey);
        TransactionEntity transaction = transactionMapperRegistry.getMapper(request).toEntity(request);
        transaction.setStatus(TransactionStatus.EXPIRED);
        transactionRepository.saveAndFlush(transaction);
        long transactionCountBefore = transactionRepository.count();

        assertThatThrownBy(() ->
                transactionService.debit(request)).isInstanceOf(TransactionExpiredException.class);
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void debit_failedTransaction_throwsTransactionAlreadyFailed() {

        String accountNumber = "888456189012345";
        String idempotencyKey = "debit-failed-test-001";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        DebitRequest request = new DebitRequest(accountNumber, 100, idempotencyKey);
        TransactionEntity transaction = transactionMapperRegistry.getMapper(request).toEntity(request);
        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.saveAndFlush(transaction);
        long transactionCountBefore = transactionRepository.count();

        assertThatThrownBy(() ->
                transactionService.debit(request)).isInstanceOf(TransactionAlreadyFailedException.class);
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void debit_concurrentRequests_onlyOneSucceeds() throws Exception {

        String accountNumber = "888856789012345";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        transactionService.credit(
                new CreditRequest(accountNumber, 100, "ebit-concurrency-credit-001"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<CompletableFuture<Boolean>> futures = IntStream.range(0, 2)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        ready.countDown();
                        try {
                            start.await();
                            transactionService.debit(
                                    new DebitRequest(accountNumber, 80, "debit-concurrency-" + i));

                            return true;

                        } catch (InsufficientFundsException e) {
                            return false;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }, executor))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(15, TimeUnit.SECONDS);
            assertThat(futures.stream().filter(CompletableFuture::join).count()).isEqualTo(1);
            assertThat(accountService.findByAccountNumber(accountNumber).getBalance())
                    .isEqualByComparingTo("20");

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -100})
    void debit_amountMustBePositive(long amount) {
        DebitRequest request = new DebitRequest(
                "888456789012345",
                amount,
                "debit-validation-test-001"
        );

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("amount must be positive");
    }

    // endregion

    // region Transfer tests

    @Test
    void transfer_happyPath_completed() {

        String sourceAccountNumber = "886456789012345";
        String destinationAccountNumber = "886456789012346";
        String idempotencyKey = "transfer-atomic-happy-001";
        accountService.createAccount(new CreateAccountRequest(sourceAccountNumber, "0012345678"));
        accountService.createAccount(new CreateAccountRequest(destinationAccountNumber, "0012345679"));
        accountService.credit(sourceAccountNumber, BigDecimal.valueOf(100));
        long transactionCountBefore = transactionRepository.count();
        TransferRequest request = new TransferRequest(
                sourceAccountNumber,
                destinationAccountNumber,
                40,
                idempotencyKey
        );

        transactionService.transfer(request);

        AccountEntity source = accountService.findByAccountNumber(sourceAccountNumber);
        AccountEntity destination = accountService.findByAccountNumber(destinationAccountNumber);
        assertThat(source.getBalance()).isEqualByComparingTo("60");
        assertThat(destination.getBalance()).isEqualByComparingTo("40");
        TransactionEntity transaction = transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transactionMapperRegistry.getMapper(request).matches(request, transaction));
    }

    @Test
    void transfer_sourceAccountDoesNotExist_noTransactionIsRecorded() {

        String sourceAccountNumber = "884456789012347";
        String destinationAccountNumber = "884456789012348";
        String idempotencyKey = "transfer-unknown-source-001";
        accountService.createAccount(new CreateAccountRequest(destinationAccountNumber, "0012345679"));
        long transactionCountBefore = transactionRepository.count();
        TransferRequest request = new TransferRequest(
                sourceAccountNumber,
                destinationAccountNumber,
                40,
                idempotencyKey);

        assertThatThrownBy(() ->
                transactionService.transfer(request))
                .isInstanceOf(UnknownAccountException.class);
        AccountEntity destination = accountService.findByAccountNumber(destinationAccountNumber);
        assertThat(destination.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transactionRepository.existsByIdempotencyKey(idempotencyKey)).isFalse();
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void transfer_destinationAccountDoesNotExist_noTransactionIsRecorded() {

        String sourceAccountNumber = "884456789012349";
        String destinationAccountNumber = "884456789012350";
        String idempotencyKey = "transfer-unknown-dest-001";
        accountService.createAccount(new CreateAccountRequest(sourceAccountNumber, "0012345678"));
        accountService.credit(sourceAccountNumber, BigDecimal.valueOf(100));
        long transactionCountBefore = transactionRepository.count();
        TransferRequest request = new TransferRequest(
                sourceAccountNumber,
                destinationAccountNumber,
                40,
                idempotencyKey);

        assertThatThrownBy(() ->
                transactionService.transfer(request))
                .isInstanceOf(UnknownAccountException.class);
        AccountEntity source = accountService.findByAccountNumber(sourceAccountNumber);
        assertThat(source.getBalance()).isEqualByComparingTo("100");
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void transfer_accountCreditFails_remainsPending() {

        String sourceAccountNumber = "886456789012347";
        String destinationAccountNumber = "886456789012348";
        String idempotencyKey = "transfer-atomic-balance-fail-001";
        accountService.createAccount(new CreateAccountRequest(sourceAccountNumber, "0012345678"));
        accountService.createAccount(new CreateAccountRequest(destinationAccountNumber, "0012345679"));
        accountService.credit(sourceAccountNumber, BigDecimal.valueOf(100));
        long transactionCountBefore = transactionRepository.count();
        TransferRequest request = new TransferRequest(
                sourceAccountNumber,
                destinationAccountNumber,
                40,
                idempotencyKey);

        doThrow(new RuntimeException("unexpected balance update failure"))
                .when(accountService)
                .credit(eq(destinationAccountNumber), any());

        assertThatThrownBy(() ->
                transactionService.transfer(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unexpected balance update failure");
        AccountEntity source = accountService.findByAccountNumber(sourceAccountNumber);
        AccountEntity destination = accountService.findByAccountNumber(destinationAccountNumber);
        assertThat(source.getBalance()).isEqualByComparingTo("100");
        assertThat(destination.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        TransactionEntity transaction = transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transactionMapperRegistry.getMapper(request).matches(request, transaction));
    }

    @Test
    void transfer_accountDebitFails_remainsPending() {
        String sourceAccountNumber = "886456789012349";
        String destinationAccountNumber = "886456789012350";
        String idempotencyKey = "transfer-atomic-debit-fail-001";
        accountService.createAccount(new CreateAccountRequest(sourceAccountNumber, "0012345678"));
        accountService.createAccount(new CreateAccountRequest(destinationAccountNumber, "0012345679"));
        accountService.credit(sourceAccountNumber, BigDecimal.valueOf(100));
        long transactionCountBefore = transactionRepository.count();
        TransferRequest request = new TransferRequest(
                sourceAccountNumber,
                destinationAccountNumber,
                40,
                idempotencyKey);

        doThrow(new RuntimeException("unexpected failure"))
                .when(accountService)
                .debit(eq(sourceAccountNumber), any());

        assertThatThrownBy(() ->
                transactionService.transfer(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unexpected failure");
        AccountEntity source = accountService.findByAccountNumber(sourceAccountNumber);
        AccountEntity destination = accountService.findByAccountNumber(destinationAccountNumber);
        assertThat(source.getBalance()).isEqualByComparingTo("100");
        assertThat(destination.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        TransactionEntity transaction = transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transactionMapperRegistry.getMapper(request).matches(request, transaction));
    }

    @Test
    void transfer_balanceIsInsufficient_transactionIsMarkedAsFailed() {

        String sourceAccountNumber = "999454489012346";
        String destinationAccountNumber = "999456789012347";
        String idempotencyKey = "it-transfer-insufficient-funds-001";
        accountService.createAccount(new CreateAccountRequest(sourceAccountNumber, "0012345678"));
        accountService.createAccount(new CreateAccountRequest(destinationAccountNumber, "0012345679"));
        long transactionCountBefore = transactionRepository.count();
        TransferRequest request = new TransferRequest(
                sourceAccountNumber,
                destinationAccountNumber,
                1,
                idempotencyKey
        );

        assertThatThrownBy(() -> transactionService.transfer(request)).isInstanceOf(InsufficientFundsException.class);
        AccountEntity source = accountService.findByAccountNumber(sourceAccountNumber);
        AccountEntity destination = accountService.findByAccountNumber(destinationAccountNumber);
        assertThat(source.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(destination.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        TransactionEntity transaction = transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transactionMapperRegistry.getMapper(request).matches(request, transaction));
    }

    @Test
    void transfer_concurrentRequestsWithSameIdempotencyKey_executesOnlyOnce() throws Exception {

        int concurrentRequests = 5;
        AccountEntity sourceAccount = accountService.createAccount(
                new CreateAccountRequest("999456789012350", "0012345678"));
        AccountEntity destinationAccount = accountService.createAccount(
                new CreateAccountRequest("999456789012351", "0012345678"));
        accountService.credit(
                sourceAccount.getAccountNumber(),
                BigDecimal.valueOf(1000));
        String idempotencyKey = "concurrent-transfer-test-001";
        TransferRequest request = new TransferRequest(
                sourceAccount.getAccountNumber(),
                destinationAccount.getAccountNumber(),
                100,
                idempotencyKey
        );
        BigDecimal initialSourceBalance = accountService
                .findByAccountNumber(sourceAccount.getAccountNumber())
                .getBalance();
        BigDecimal initialDestinationBalance = accountService
                .findByAccountNumber(destinationAccount.getAccountNumber())
                .getBalance();
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<CompletableFuture<Void>> futures = IntStream
                    .range(0, concurrentRequests)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        ready.countDown();
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                        transactionService.transfer(request);
                    }, executor))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            ).get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        AccountEntity updatedSourceAccount = accountService
                .findByAccountNumber(sourceAccount.getAccountNumber());
        AccountEntity updatedDestinationAccount = accountService
                .findByAccountNumber(destinationAccount.getAccountNumber());
        assertThat(updatedSourceAccount.getBalance())
                .isEqualByComparingTo(initialSourceBalance.subtract(BigDecimal.valueOf(100)));
        assertThat(updatedDestinationAccount.getBalance())
                .isEqualByComparingTo(initialDestinationBalance.add(BigDecimal.valueOf(100)));
        TransactionEntity transaction = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow();
        assertThat(transaction.getStatus())
                .isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void transfer_sameIdempotencyKeyWithDifferentPayload_throwsIdempotencyKeyConflictException() {

        AccountEntity sourceAccount = accountService.createAccount(
                new CreateAccountRequest("999456789012354", "0012345678"));
        AccountEntity destinationAccount = accountService.createAccount(
                new CreateAccountRequest("999456789012355", "0012345678"));
        accountService.credit(sourceAccount.getAccountNumber(), BigDecimal.valueOf(1000));
        String idempotencyKey = "transfer-conflict-test-001";
        TransferRequest firstRequest = new TransferRequest(
                sourceAccount.getAccountNumber(),
                destinationAccount.getAccountNumber(),
                100,
                idempotencyKey);
        TransferRequest secondRequest = new TransferRequest(
                sourceAccount.getAccountNumber(),
                destinationAccount.getAccountNumber(),
                200,
                idempotencyKey);
        BigDecimal initialSourceBalance = accountService
                .findByAccountNumber(sourceAccount.getAccountNumber()).getBalance();
        BigDecimal initialDestinationBalance = accountService
                .findByAccountNumber(destinationAccount.getAccountNumber()).getBalance();

        transactionService.transfer(firstRequest);

        assertThatThrownBy(() -> transactionService.transfer(secondRequest))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        AccountEntity updatedSourceAccount = accountService.findByAccountNumber(sourceAccount.getAccountNumber());
        AccountEntity updatedDestinationAccount = accountService.findByAccountNumber(destinationAccount.getAccountNumber());
        assertThat(updatedSourceAccount.getBalance())
                .isEqualByComparingTo(initialSourceBalance.subtract(BigDecimal.valueOf(100)));
        assertThat(updatedDestinationAccount.getBalance())
                .isEqualByComparingTo(initialDestinationBalance.add(BigDecimal.valueOf(100)));
        TransactionEntity transaction = transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transactionMapperRegistry.getMapper(firstRequest).matches(firstRequest, transaction)).isTrue();
    }

    @Test
    void transfer_expiredTransaction_throwsTransactionExpiredException() {

        String sourceAccountNumber = "888456785172345";
        String destinationAccountNumber = "888111789012346";
        String idempotencyKey = "transfer-expired-test-001";

        accountService.createAccount(new CreateAccountRequest(sourceAccountNumber, "0012345678"));
        accountService.createAccount(new CreateAccountRequest(destinationAccountNumber, "0012345679"));

        TransferRequest request = new TransferRequest(sourceAccountNumber, destinationAccountNumber, 100, idempotencyKey);
        TransactionEntity transaction = transactionMapperRegistry.getMapper(request).toEntity(request);
        transaction.setStatus(TransactionStatus.EXPIRED);
        transactionRepository.saveAndFlush(transaction);
        long transactionCountBefore = transactionRepository.count();

        assertThatThrownBy(() ->
                transactionService.transfer(request)).isInstanceOf(TransactionExpiredException.class);
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void transfer_identicalSourceAndDestination_noTransactionIsRecorded() {

        String sourceAccountNumber = "884456734012347";
        String destinationAccountNumber = "884456734012347";
        String idempotencyKey = "transfer-identical-sources-001";
        accountService.createAccount(new CreateAccountRequest(destinationAccountNumber, "0012345679"));
        long transactionCountBefore = transactionRepository.count();
        TransferRequest request = new TransferRequest(
                sourceAccountNumber,
                destinationAccountNumber,
                40,
                idempotencyKey);

        assertThatThrownBy(() ->
                transactionService.transfer(request))
                .isInstanceOf(SameSourceAndDestinationTransferException.class);
        AccountEntity destination = accountService.findByAccountNumber(destinationAccountNumber);
        assertThat(destination.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transactionRepository.existsByIdempotencyKey(idempotencyKey)).isFalse();
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }
    @Test
    void transfer_failedTransaction_throwsTransactionAlreadyFailed() {

        String sourceAccountNumber = "888451167012345";
        String destinationAccountNumber = "888453159012346";
        String idempotencyKey = "transfer-failed-test-001";

        accountService.createAccount(new CreateAccountRequest(sourceAccountNumber, "0012345678"));
        accountService.createAccount(new CreateAccountRequest(destinationAccountNumber, "0012345679"));

        TransferRequest request = new TransferRequest(sourceAccountNumber, destinationAccountNumber, 100, idempotencyKey);
        TransactionEntity transaction = transactionMapperRegistry.getMapper(request).toEntity(request);
        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.saveAndFlush(transaction);
        long transactionCountBefore = transactionRepository.count();

        assertThatThrownBy(() ->
                transactionService.transfer(request)).isInstanceOf(TransactionAlreadyFailedException.class);
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void transfer_concurrentRequests_bothSucceed() throws Exception {

        String sourceAccountNumber = "888415789012345";
        String destinationAAccountNumber = "888456789012346";
        String destinationBAccountNumber = "888456555012347";
        accountService.createAccount(new CreateAccountRequest(sourceAccountNumber, "0012345678"));
        accountService.createAccount(new CreateAccountRequest(destinationAAccountNumber, "0012345679"));
        accountService.createAccount(new CreateAccountRequest(destinationBAccountNumber, "0012345680"));
        transactionService.credit(
                new CreditRequest(sourceAccountNumber, 200, "transfer-concurrency-credit-001"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<CompletableFuture<Boolean>> futures = List.of(
                    CompletableFuture.supplyAsync(() -> {
                        ready.countDown();
                        try {
                            start.await();

                            transactionService.transfer(
                                    new TransferRequest(
                                            sourceAccountNumber,
                                            destinationAAccountNumber,
                                            80,
                                            "transfer-concurrency-001"
                                    ));

                            return true;

                        } catch (ObjectOptimisticLockingFailureException e) {
                            return false;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }, executor),

                    CompletableFuture.supplyAsync(() -> {
                        ready.countDown();

                        try {
                            start.await();

                            transactionService.transfer(
                                    new TransferRequest(
                                            sourceAccountNumber,
                                            destinationBAccountNumber,
                                            80,
                                            "transfer-concurrency-002"
                                    ));

                            return true;

                        } catch (ObjectOptimisticLockingFailureException e) {
                            return false;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }, executor)
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(15, TimeUnit.SECONDS);
            assertThat(futures.stream().filter(CompletableFuture::join).count()).isEqualTo(2);
            assertThat(accountService.findByAccountNumber(sourceAccountNumber).getBalance())
                    .isEqualByComparingTo("40");
            assertThat(accountService.findByAccountNumber(destinationAAccountNumber).getBalance())
                    .isEqualByComparingTo("80");
            assertThat(accountService.findByAccountNumber(destinationBAccountNumber).getBalance())
                    .isEqualByComparingTo("80");

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -100})
    void transfer_amountMustBePositive(long amount) {
        TransferRequest request = new TransferRequest(
                "888456789012345",
                "888456789012346",
                amount,
                "transfer-validation-test-001");

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("amount must be positive");
    }

    // endregion











}
