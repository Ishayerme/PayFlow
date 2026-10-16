package com.payflow.account.account;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record AccountResponse(Long id, Long userId, String accountNumber, BigDecimal balance,
                                  AccountStatus status, Instant createdAt) {
        public static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getUserId(), a.getAccountNumber(), a.getBalance(),
                    a.getStatus(), a.getCreatedAt());
        }
    }

    /** @Digits rejects amounts like 10.999: money has at most two decimal places. */
    public record AmountRequest(
            @NotNull @Positive @DecimalMax("1000000.00") @Digits(integer = 17, fraction = 2) BigDecimal amount) {
    }

    public record StatusRequest(@NotNull AccountStatus status) {
    }
}
