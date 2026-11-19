package ir.challange.bank.transaction;


import ir.challange.bank.transaction.dto.CreditRequest;
import ir.challange.bank.transaction.dto.DebitRequest;
import ir.challange.bank.transaction.dto.TransferRequest;

public interface TransactionService {

    void credit(CreditRequest request);

    void debit(DebitRequest request);

    void transfer(TransferRequest request);

}
