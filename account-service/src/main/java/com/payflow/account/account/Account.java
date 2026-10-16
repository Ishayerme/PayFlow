package com.payflow.account.account;

import com.payflow.common.error.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;

    // BigDecimal, never double: 0.1 + 0.2 must equal exactly 0.3 when it's money.
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Account() {
        // for JPA
    }

    public Account(Long userId, String accountNumber) {
        this.userId = userId;
        this.accountNumber = accountNumber;
    }

    public void deposit(BigDecimal amount) {
        requireActive();
        balance = balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        requireActive();
        if (balance.compareTo(amount) < 0) {
            throw ApiException.conflict("Insufficient funds in account " + accountNumber);
        }
        balance = balance.subtract(amount);
    }

    private void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw ApiException.conflict("Account " + accountNumber + " is " + status);
        }
    }

    public void setStatus(AccountStatus status) { this.status = status; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getAccountNumber() { return accountNumber; }
    public BigDecimal getBalance() { return balance; }
    public AccountStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
