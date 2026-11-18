package ir.challange.bank.transaction;


import ir.challange.bank.account.dto.CreditRequest;
import ir.challange.bank.account.dto.DebitRequest;
import ir.challange.bank.account.dto.TransferRequest;

public interface TransactionService {

    void credit(CreditRequest request);

    void debit(DebitRequest request);

    void transfer(TransferRequest request);

}
