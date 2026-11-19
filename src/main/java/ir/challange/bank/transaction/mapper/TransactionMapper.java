package ir.challange.bank.transaction.mapper;

import ir.challange.bank.transaction.dto.TransactionRequest;
import ir.challange.bank.transaction.TransactionEntity;

public interface TransactionMapper<T extends TransactionRequest> {

    TransactionEntity toEntity(T request);

    Class<T> getRequestType();

    boolean matches(T request, TransactionEntity existing);

}
