package ir.challange.bank.transaction;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransactionEntity t where t.idempotencyKey = :key")
    Optional<TransactionEntity> findByIdempotencyKeyForUpdate(String key);

    @EntityGraph(attributePaths = {
            "sourceAccount",
            "destinationAccount"
    })
    Optional<TransactionEntity> findByIdempotencyKey(String key);

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
    update TransactionEntity t
    set t.status = ir.challange.bank.transaction.TransactionStatus.EXPIRED
    where t.status = ir.challange.bank.transaction.TransactionStatus.PENDING
      and t.timestamp < :cutoff
    """)
    int expirePendingOlderThan(Instant cutoff);
}
