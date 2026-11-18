package ir.challange.bank.transaction.integration;

import ir.challange.bank.account.AccountEntity;
import ir.challange.bank.account.AccountService;
import ir.challange.bank.account.dto.CreateAccountRequest;
import ir.challange.bank.account.dto.CreditRequest;
import ir.challange.bank.account.dto.DebitRequest;
import ir.challange.bank.account.dto.TransferRequest;
import ir.challange.bank.account.exception.InsufficientFundsException;
import ir.challange.bank.account.exception.UnknownAccountException;
import ir.challange.bank.transaction.TransactionRepository;
import ir.challange.bank.transaction.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;

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

    // region Credit tests

    @Test
    void credit_happyPath() {

        String accountNumber = "888456789012345";
        String idempotencyKey = "credit-atomic-happy-001";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));

        transactionService.credit(new CreditRequest(accountNumber, 100, idempotencyKey));

        AccountEntity account = accountService.findByAccountNumber(accountNumber);
        assertThat(account.getBalance()).isEqualByComparingTo("100");
        assertThat(transactionRepository.existsByIdempotencyKey(idempotencyKey)).isTrue();
    }

    @Test
    void credit_rejectsWhenAccountDoesNotExist() {

        String accountNumber = "884456789012345";
        String idempotencyKey = "credit-unknown-account-001";
        long transactionCountBefore = transactionRepository.count();

        assertThatThrownBy(() ->
                transactionService.credit(new CreditRequest(accountNumber, 100, idempotencyKey)))
                .isInstanceOf(UnknownAccountException.class);
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void credit_rollsBackWhenAccountCreditFails() {

        String accountNumber = "888456789012347";
        String idempotencyKey = "credit-atomic-balance-fail-001";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        long transactionCountBefore = transactionRepository.count();

        doThrow(new RuntimeException("unexpected balance update failure"))
                .when(accountService)
                .credit(any(), any());

        assertThatThrownBy(() ->
                transactionService.credit(new CreditRequest(accountNumber, 100, idempotencyKey)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unexpected balance update failure");
        AccountEntity account = accountService.findByAccountNumber(accountNumber);
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transactionRepository.existsByIdempotencyKey(idempotencyKey)).isFalse();
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    // endregion

    // region Debit tests

    @Test
    void debit_happyPath() {

        String accountNumber = "887456789012345";
        String idempotencyKey = "debit-atomic-happy-001";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        accountService.credit(accountNumber, BigDecimal.valueOf(100));

        transactionService.debit(new DebitRequest(accountNumber, 40, idempotencyKey));

        AccountEntity account = accountService.findByAccountNumber(accountNumber);
        assertThat(account.getBalance()).isEqualByComparingTo("60");
        assertThat(transactionRepository.existsByIdempotencyKey(idempotencyKey)).isTrue();
    }

    @Test
    void debit_rejectsWhenAccountDoesNotExist() {

        String accountNumber = "884456789012346";
        String idempotencyKey = "debit-unknown-account-001";
        long transactionCountBefore = transactionRepository.count();

        assertThatThrownBy(() ->
                transactionService.debit(new DebitRequest(accountNumber, 40, idempotencyKey)))
                .isInstanceOf(UnknownAccountException.class);
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void debit_rollsBackWhenAccountDebitFails() {

        String accountNumber = "887456789012346";
        String idempotencyKey = "debit-atomic-balance-fail-001";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        accountService.credit(accountNumber, BigDecimal.valueOf(100));
        long transactionCountBefore = transactionRepository.count();

        doThrow(new RuntimeException("unexpected balance update failure"))
                .when(accountService)
                .debit(any(), any());

        assertThatThrownBy(() ->
                transactionService.debit(new DebitRequest(accountNumber, 40, idempotencyKey)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unexpected balance update failure");
        AccountEntity account = accountService.findByAccountNumber(accountNumber);
        assertThat(account.getBalance()).isEqualByComparingTo("100");
        assertThat(transactionRepository.existsByIdempotencyKey(idempotencyKey)).isFalse();
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void debit_rejectsWhenBalanceIsInsufficient() {

        String accountNumber = "999456789012345";
        accountService.createAccount(new CreateAccountRequest(accountNumber, "0012345678"));
        long transactionCountBefore = transactionRepository.count();
        DebitRequest request = new DebitRequest(accountNumber, 1, "it-debit-insufficient-funds-001");

        assertThatThrownBy(() -> transactionService.debit(request)).isInstanceOf(InsufficientFundsException.class);
        AccountEntity source = accountService.findByAccountNumber(accountNumber);
        assertThat(source.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    // endregion

    // region Transfer tests

    @Test
    void transfer_happyPath() {

        String sourceAccountNumber = "886456789012345";
        String destinationAccountNumber = "886456789012346";
        String idempotencyKey = "transfer-atomic-happy-001";
        accountService.createAccount(new CreateAccountRequest(sourceAccountNumber, "0012345678"));
        accountService.createAccount(new CreateAccountRequest(destinationAccountNumber, "0012345679"));
        accountService.credit(sourceAccountNumber, BigDecimal.valueOf(100));
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
        assertThat(transactionRepository.existsByIdempotencyKey(idempotencyKey)).isTrue();
    }

    @Test
    void transfer_rejectsWhenSourceAccountDoesNotExist() {

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
    void transfer_rejectsWhenDestinationAccountDoesNotExist() {

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
    void transfer_rollsBackWhenAccountCreditFails() {

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
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void transfer_rollsBackWhenAccountDebitFails() {
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

        doThrow(new RuntimeException("unexpected balance update failure"))
                .when(accountService)
                .debit(eq(sourceAccountNumber), any());

        assertThatThrownBy(() ->
                transactionService.transfer(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unexpected balance update failure");
        AccountEntity source = accountService.findByAccountNumber(sourceAccountNumber);
        AccountEntity destination = accountService.findByAccountNumber(destinationAccountNumber);
        assertThat(source.getBalance()).isEqualByComparingTo("100");
        assertThat(destination.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void transfer_rejectsWhenSourceBalanceIsInsufficient() {

        String sourceAccountNumber = "999456789012346";
        String destinationAccountNumber = "999456789012347";
        accountService.createAccount(new CreateAccountRequest(sourceAccountNumber, "0012345678"));
        accountService.createAccount(new CreateAccountRequest(destinationAccountNumber, "0012345679"));
        long transactionCountBefore = transactionRepository.count();
        TransferRequest request = new TransferRequest(
                sourceAccountNumber,
                destinationAccountNumber,
                1,
                "it-transfer-insufficient-funds-001"
        );

        assertThatThrownBy(() -> transactionService.transfer(request)).isInstanceOf(InsufficientFundsException.class);
        AccountEntity source = accountService.findByAccountNumber(sourceAccountNumber);
        AccountEntity destination = accountService.findByAccountNumber(destinationAccountNumber);
        assertThat(source.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(destination.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    // endregion

}
