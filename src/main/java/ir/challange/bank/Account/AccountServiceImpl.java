package ir.challange.bank.Account;

import ir.challange.bank.Account.dto.CreateAccountRequest;
import ir.challange.bank.Account.dto.CreditRequest;
import ir.challange.bank.Account.dto.DebitRequest;
import ir.challange.bank.Account.dto.TransferRequest;
import ir.challange.bank.Account.exception.AccountAlreadyExistsException;
import ir.challange.bank.Account.exception.AccountNotFoundException;
import ir.challange.bank.Account.exception.InsufficientFundsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public AccountEntity createAccount(CreateAccountRequest request) {

        if (accountRepository.existsByAccountNumber(request.accountNumber()))
            throw new AccountAlreadyExistsException(request.accountNumber());

        AccountEntity account = AccountEntity
                .builder()
                .accountNumber(request.accountNumber())
                .ownerSsn(request.ownerSsn())
                .balance(BigDecimal.ZERO)
                .build();

        return accountRepository.save(account);
    }

    @Override
    @Transactional
    public void credit(CreditRequest request) {

        AccountEntity account = accountRepository.findByAccountNumber(request.accountNumber())
                .orElseThrow(() -> new AccountNotFoundException(request.accountNumber()));

        account.setBalance(account.getBalance().add(BigDecimal.valueOf(request.amount())));
    }

    @Override
    @Transactional
    public void debit(DebitRequest request) {

        AccountEntity account = accountRepository.findByAccountNumber(request.accountNumber())
                .orElseThrow(() -> new AccountNotFoundException(request.accountNumber()));

        BigDecimal debitAmount = BigDecimal.valueOf(request.amount());
        if (account.getBalance().compareTo(debitAmount) < 0)
            throw new InsufficientFundsException(request.accountNumber());
        account.setBalance(account.getBalance().subtract(debitAmount));
    }

    @Override
    @Transactional
    public void transfer(TransferRequest request) {

        AccountEntity sourceAccount = accountRepository.findByAccountNumber(request.sourceAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(request.sourceAccountNumber()));
        AccountEntity destinationAccount = accountRepository.findByAccountNumber(request.destinationAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(request.destinationAccountNumber()));

        BigDecimal transferAmount = BigDecimal.valueOf(request.amount());
        if (sourceAccount.getBalance().compareTo(transferAmount) < 0)
            throw new InsufficientFundsException(request.sourceAccountNumber());
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(transferAmount));
        destinationAccount.setBalance(destinationAccount.getBalance().add(transferAmount));
    }

}
