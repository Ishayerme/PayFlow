package com.payflow.transaction.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionRecord, Long> {

    /**
     * Latest transactions where the account sent OR received money.
     *
     * Written as two indexed halves joined by UNION ALL instead of
     * "WHERE from = ? OR to = ?": each half can walk its own (account, created_at)
     * index in date order and stop after :limit rows. The test
     * StatementQueryPlanTest runs EXPLAIN ANALYZE on this exact SQL to prove it.
     *
     * :accountId and :limit are bound parameters, never string-concatenated.
     */
    String STATEMENT_SQL = """
            (SELECT * FROM transactions WHERE from_account_id = :accountId ORDER BY created_at DESC LIMIT :limit)
            UNION ALL
            (SELECT * FROM transactions WHERE to_account_id = :accountId ORDER BY created_at DESC LIMIT :limit)
            ORDER BY created_at DESC
            LIMIT :limit
            """;

    @Query(value = STATEMENT_SQL, nativeQuery = true)
    List<TransactionRecord> findStatement(@Param("accountId") Long accountId, @Param("limit") int limit);
}
