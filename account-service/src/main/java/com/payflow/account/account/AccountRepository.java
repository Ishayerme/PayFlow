package com.payflow.account.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUserIdOrderByIdAsc(Long userId);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Loads the account AND takes a row-level write lock on it:
     *   SELECT ... FROM accounts WHERE id = ? FOR UPDATE
     *
     * Any other transaction that tries to lock the same row waits until this
     * transaction commits or rolls back. That is what stops two transfers from
     * reading the same old balance at the same time ("lost update").
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    /** Sum of every balance. Transfers move money around, so this must never change. */
    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a")
    BigDecimal totalBalance();
}
