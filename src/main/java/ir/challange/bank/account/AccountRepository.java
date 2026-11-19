package ir.challange.bank.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    boolean existsByAccountNumber(String accountNumber);

    Optional<AccountEntity> findByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM AccountEntity a
            WHERE a.accountNumber = :accountNumber
            """)
    Optional<AccountEntity> findByAccountNumberForUpdate(String accountNumber);

}
