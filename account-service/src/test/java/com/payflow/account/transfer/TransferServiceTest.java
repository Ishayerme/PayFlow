package com.payflow.account.transfer;

import com.payflow.account.account.Account;
import com.payflow.account.account.AccountRepository;
import com.payflow.account.account.AccountStatus;
import com.payflow.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock AccountRepository accounts;
    @InjectMocks TransferService transferService;

    private Account stub(Long id, Long userId, String balance) {
        Account account = new Account(userId, "PF000000000" + id);
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "balance", new BigDecimal(balance));
        when(accounts.findByIdForUpdate(id)).thenReturn(Optional.of(account));
        return account;
    }

    @Test
    void transferMovesMoneyBetweenAccounts() {
        Account from = stub(1L, 100L, "500.00");
        Account to = stub(2L, 200L, "20.00");

        var result = transferService.transfer(1L, 2L, new BigDecimal("120.00"), 100L);

        assertThat(from.getBalance()).isEqualByComparingTo("380.00");
        assertThat(to.getBalance()).isEqualByComparingTo("140.00");
        assertThat(result.fromOwnerId()).isEqualTo(100L);
        assertThat(result.toOwnerId()).isEqualTo(200L);
    }

    @Test
    void locksAreTakenInAscendingIdOrderEvenWhenSendingToLowerId() {
        stub(5L, 100L, "500.00");   // sender has the HIGHER id
        stub(3L, 200L, "0.00");

        transferService.transfer(5L, 3L, BigDecimal.ONE, 100L);

        // Lower id must be locked first; this ordering is what prevents deadlocks.
        InOrder order = inOrder(accounts);
        order.verify(accounts).findByIdForUpdate(3L);
        order.verify(accounts).findByIdForUpdate(5L);
    }

    @Test
    void insufficientFundsIsConflictAndNothingChanges() {
        Account from = stub(1L, 100L, "50.00");
        Account to = stub(2L, 200L, "0.00");

        assertThatThrownBy(() -> transferService.transfer(1L, 2L, new BigDecimal("50.01"), 100L))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        assertThat(from.getBalance()).isEqualByComparingTo("50.00");
        assertThat(to.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void cannotSendFromSomeoneElsesAccount() {
        stub(1L, 100L, "500.00");
        stub(2L, 200L, "0.00");

        assertThatThrownBy(() -> transferService.transfer(1L, 2L, BigDecimal.TEN, 999L))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void transferToFrozenAccountIsRejected() {
        stub(1L, 100L, "500.00");
        Account to = stub(2L, 200L, "0.00");
        to.setStatus(AccountStatus.FROZEN);

        assertThatThrownBy(() -> transferService.transfer(1L, 2L, BigDecimal.TEN, 100L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("FROZEN");
    }

    @Test
    void sameAccountTransferIsBadRequestAndTakesNoLocks() {
        assertThatThrownBy(() -> transferService.transfer(1L, 1L, BigDecimal.TEN, 100L))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(accounts, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void zeroOrNegativeAmountIsBadRequest() {
        assertThatThrownBy(() -> transferService.transfer(1L, 2L, BigDecimal.ZERO, 100L))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> transferService.transfer(1L, 2L, new BigDecimal("-5"), 100L))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void missingAccountIsNotFound() {
        stub(1L, 100L, "500.00");
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(1L, 2L, BigDecimal.TEN, 100L))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }
}
