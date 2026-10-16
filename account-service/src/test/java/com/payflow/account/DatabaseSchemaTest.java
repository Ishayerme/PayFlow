package com.payflow.account;

import com.payflow.account.config.AdminSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EmbeddedPostgresTest
class DatabaseSchemaTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired AdminSeeder adminSeeder;

    @Test
    void databaseRejectsNegativeBalanceEvenIfCodeTriedIt() {
        jdbc.execute("TRUNCATE accounts, users RESTART IDENTITY CASCADE");
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, role) VALUES ('a@b.com','x','A','CUSTOMER') RETURNING id",
                Long.class);
        jdbc.update("INSERT INTO accounts (user_id, account_number, balance, status) VALUES (?, 'PF1', 10, 'ACTIVE')", userId);

        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET balance = -1 WHERE account_number = 'PF1'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("balance_not_negative");
    }

    @Test
    void adminSeederCreatesOneAdminWithHashedPasswordAndIsIdempotent() {
        jdbc.execute("TRUNCATE accounts, users RESTART IDENTITY CASCADE");

        adminSeeder.run(null);
        adminSeeder.run(null);   // second start-up must not create a duplicate

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'ADMIN'", Integer.class)).isEqualTo(1);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE role = 'ADMIN'", String.class);
        assertThat(hash).startsWith("$2a$").doesNotContain("Admin@12345");
    }
}
