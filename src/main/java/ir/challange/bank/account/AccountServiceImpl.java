package ir.challange.bank.account;

import ir.challange.bank.account.dto.CreateAccountRequest;
import ir.challange.bank.account.exception.AccountAlreadyExistsException;
import ir.challange.bank.account.exception.InsufficientFundsException;
import ir.challange.bank.account.exception.UnknownAccountException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    @Transactional(readOnly = true)
    public AccountEntity findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new UnknownAccountException(accountNumber));
    }

    private AccountEntity findByAccountNumberForUpdate(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new UnknownAccountException(accountNumber));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void credit(String accountNumber, BigDecimal amount) {

        AccountEntity account = findByAccountNumberForUpdate(accountNumber);
        account.setBalance(account.getBalance().add(amount));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void debit(String accountNumber, BigDecimal amount) {

        AccountEntity account = findByAccountNumberForUpdate(accountNumber);
        if (account.getBalance().compareTo(amount) < 0)
            throw new InsufficientFundsException(accountNumber);
        account.setBalance(account.getBalance().subtract(amount));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transfer(String sourceAccountNumber, String destinationAccountNumber, BigDecimal amount) {
        if (sourceAccountNumber.compareTo(destinationAccountNumber) < 0) {
            debit(sourceAccountNumber, amount);
            credit(destinationAccountNumber, amount);
        } else {
            credit(destinationAccountNumber, amount);
            debit(sourceAccountNumber, amount);
        }
    }
}
