package ir.challange.bank.transaction.dto;


public interface TransactionRequest {
    String idempotencyKey();
}
