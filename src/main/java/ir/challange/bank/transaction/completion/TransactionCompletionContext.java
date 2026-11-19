package ir.challange.bank.transaction.completion;

import ir.challange.bank.transaction.TransactionEntity;

public record TransactionCompletionContext(TransactionEntity transaction, Runnable moneyOperation) {
}