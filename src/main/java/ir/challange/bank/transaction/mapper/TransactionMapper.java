package ir.challange.bank.transaction.mapper;

import ir.challange.bank.transaction.RecordableTransactionRequest;
import ir.challange.bank.transaction.TransactionEntity;

public interface TransactionMapper<T extends RecordableTransactionRequest> {

    TransactionEntity toEntity(T request);

    Class<T> getRequestType();
}
