package com.payflow.transaction.ledger;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the statement query uses the indexes, with EXPLAIN ANALYZE on a
 * realistic amount of data in a real PostgreSQL.
 */
@SpringBootTest
@AutoConfigureEmbeddedDatabase(type = DatabaseType.POSTGRES, provider = DatabaseProvider.ZONKY)
class StatementQueryPlanTest {

    private static final int ROWS = 50_000;
    private static final int ACCOUNTS = 500;

    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionRepository repository;

    @BeforeEach
    void loadRealisticData() {
        jdbc.execute("TRUNCATE transactions RESTART IDENTITY");
        // 50,000 transactions spread over 500 accounts and the last ~35 days.
        jdbc.update("""
                INSERT INTO transactions (reference, from_account_id, to_account_id, amount, status, initiated_by, created_at)
                SELECT gen_random_uuid(),
                       (g % ?) + 1,
                       ((g * 7) % ?) + 1,
                       (g % 500) + 1,
                       'COMPLETED',
                       1,
                       now() - (g || ' minutes')::interval
                FROM generate_series(1, ?) AS g
                """, ACCOUNTS, ACCOUNTS, ROWS);
        jdbc.execute("ANALYZE transactions");   // refresh planner statistics, as autovacuum would
    }

    @Test
    void explainAnalyzeShowsBothIndexesAndNoFullTableScan() {
        String sql = TransactionRepository.STATEMENT_SQL
                .replace(":accountId", "42")
                .replace(":limit", "20");

        List<String> plan = jdbc.queryForList("EXPLAIN ANALYZE " + sql, String.class);
        String planText = String.join("\n", plan);
        System.out.println("Statement query plan:\n" + planText);

        assertThat(planText)
                .contains("idx_transactions_from_created")
                .contains("idx_transactions_to_created")
                .doesNotContain("Seq Scan");
    }

    @Test
    void statementReturnsSentAndReceivedNewestFirst() {
        List<TransactionRecord> statement = repository.findStatement(42L, 20);

        assertThat(statement).hasSize(20);
        assertThat(statement).allSatisfy(t ->
                assertThat(t.getFromAccountId().equals(42L) || t.getToAccountId().equals(42L)).isTrue());
        assertThat(statement).extracting(TransactionRecord::getCreatedAt)
                .isSortedAccordingTo((a, b) -> b.compareTo(a));
    }
}
