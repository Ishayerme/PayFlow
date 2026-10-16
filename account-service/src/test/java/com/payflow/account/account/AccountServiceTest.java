package com.payflow.account.account;

import com.payflow.common.error.ApiException;
import com.payflow.common.security.AuthUser;
import com.payflow.common.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final AuthUser OWNER = new AuthUser(1L, "owner@example.com", Role.CUSTOMER);
    private static final AuthUser STRANGER = new AuthUser(2L, "other@example.com", Role.CUSTOMER);
    private static final AuthUser ADMIN = new AuthUser(9L, "admin@example.com", Role.ADMIN);

    @Mock AccountRepository accounts;
    @InjectMocks AccountService accountService;

    private static Account account(Long id, Long userId, String balance) {
        Account account = new Account(userId, "PF000000000" + id);
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "balance", new BigDecimal(balance));
        return account;
    }

    @Test
    void openCreatesAccountWithPfNumberAndRetriesOnCollision() {
        when(accounts.existsByAccountNumber(anyString())).thenReturn(true, false);
        when(accounts.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account opened = accountService.open(OWNER);

        assertThat(opened.getAccountNumber()).matches("PF\\d{10}");
        assertThat(opened.getUserId()).isEqualTo(1L);
        assertThat(opened.getBalance()).isEqualByComparingTo("0");
        verify(accounts, times(2)).existsByAccountNumber(anyString());
    }

    @Test
    void ownerCanReadOwnAccount() {
        when(accounts.findById(10L)).thenReturn(Optional.of(account(10L, 1L, "50.00")));

        assertThat(accountService.get(10L, OWNER).getId()).isEqualTo(10L);
    }

    @Test
    void strangerGetsNotFoundForSomeoneElsesAccount() {
        when(accounts.findById(10L)).thenReturn(Optional.of(account(10L, 1L, "50.00")));

        assertThatThrownBy(() -> accountService.get(10L, STRANGER))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminCanReadAnyAccount() {
        when(accounts.findById(10L)).thenReturn(Optional.of(account(10L, 1L, "50.00")));

        assertThat(accountService.get(10L, ADMIN).getUserId()).isEqualTo(1L);
    }

    @Test
    void depositLocksRowAndIncreasesBalance() {
        when(accounts.findByIdForUpdate(10L)).thenReturn(Optional.of(account(10L, 1L, "50.00")));

        Account updated = accountService.deposit(10L, new BigDecimal("25.50"), OWNER);

        assertThat(updated.getBalance()).isEqualByComparingTo("75.50");
        verify(accounts).findByIdForUpdate(10L);
    }

    @Test
    void depositIntoFrozenAccountIsRejected() {
        Account frozen = account(10L, 1L, "50.00");
        frozen.setStatus(AccountStatus.FROZEN);
        when(accounts.findByIdForUpdate(10L)).thenReturn(Optional.of(frozen));

        assertThatThrownBy(() -> accountService.deposit(10L, BigDecimal.TEN, OWNER))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void setStatusFreezesAccount() {
        when(accounts.findByIdForUpdate(10L)).thenReturn(Optional.of(account(10L, 1L, "50.00")));

        assertThat(accountService.setStatus(10L, AccountStatus.FROZEN).getStatus()).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    void listsOnlyOwnAccountsForCustomerAndAllForAdmin() {
        when(accounts.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(account(10L, 1L, "1.00")));
        when(accounts.findAll()).thenReturn(List.of(account(10L, 1L, "1.00"), account(11L, 2L, "2.00")));

        assertThat(accountService.listOwn(OWNER)).hasSize(1);
        assertThat(accountService.listAll()).hasSize(2);
    }

    @Test
    void missingAccountIsNotFound() {
        when(accounts.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.find(99L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("99");
    }
}
