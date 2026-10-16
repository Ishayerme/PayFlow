package com.payflow.transaction.ledger;

import com.payflow.common.error.ApiException;
import com.payflow.common.security.AuthUser;
import com.payflow.common.security.Role;
import com.payflow.transaction.client.AccountClient;
import com.payflow.transaction.client.AccountClient.AccountOwner;
import com.payflow.transaction.client.AccountClient.TransferCommand;
import com.payflow.transaction.client.AccountClient.TransferResult;
import com.payflow.transaction.client.NotificationClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferOrchestratorTest {

    private static final AuthUser ISHA = new AuthUser(100L, "isha@example.com", Role.CUSTOMER);
    private static final AuthUser ADMIN = new AuthUser(9L, "admin@example.com", Role.ADMIN);
    private static final BigDecimal AMOUNT = new BigDecimal("40.00");

    @Mock TransactionRepository transactions;
    @Mock AccountClient accountClient;
    @Mock NotificationClient notificationClient;
    @InjectMocks TransferOrchestrator orchestrator;

    @Test
    void successfulTransferIsCompletedAndBothPartiesNotified() {
        when(accountClient.owner(1L)).thenReturn(new AccountOwner(1L, 100L));
        when(transactions.save(any(TransactionRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountClient.transfer(new TransferCommand(1L, 2L, AMOUNT, 100L))).thenReturn(
                new TransferResult(1L, 100L, new BigDecimal("60.00"), 2L, 200L, new BigDecimal("40.00")));

        TransactionRecord record = orchestrator.transfer(1L, 2L, AMOUNT, ISHA);

        assertThat(record.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(record.getInitiatedBy()).isEqualTo(100L);
        verify(notificationClient).send(eq(100L), contains("Sent 40.00"));
        verify(notificationClient).send(eq(200L), contains("Received 40.00"));
    }

    @Test
    void rejectedTransferIsRecordedAsFailedWithReasonAndRethrown() {
        when(accountClient.owner(1L)).thenReturn(new AccountOwner(1L, 100L));
        when(transactions.save(any(TransactionRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountClient.transfer(any())).thenThrow(ApiException.conflict("Insufficient funds in account PF1"));

        assertThatThrownBy(() -> orchestrator.transfer(1L, 2L, AMOUNT, ISHA))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);

        var saved = org.mockito.ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactions, org.mockito.Mockito.times(2)).save(saved.capture());
        TransactionRecord last = saved.getValue();
        assertThat(last.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(last.getFailureReason()).isEqualTo("Insufficient funds in account PF1");
        verify(notificationClient).send(eq(100L), contains("failed"));
    }

    @Test
    void sendingFromSomeoneElsesAccountIsForbiddenAndNeverRecorded() {
        when(accountClient.owner(1L)).thenReturn(new AccountOwner(1L, 999L));

        assertThatThrownBy(() -> orchestrator.transfer(1L, 2L, AMOUNT, ISHA))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
        // Nothing lands in the real owner's statement, and no money call is made.
        verify(transactions, never()).save(any());
        verify(accountClient, never()).transfer(any());
    }

    @Test
    void customerCannotReadStatementOfSomeoneElsesAccount() {
        when(accountClient.owner(5L)).thenReturn(new AccountOwner(5L, 999L));

        assertThatThrownBy(() -> orchestrator.statement(5L, 20, ISHA))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
        verify(transactions, never()).findStatement(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void ownerGetsStatementAndLimitIsClampedTo100() {
        when(accountClient.owner(5L)).thenReturn(new AccountOwner(5L, 100L));
        when(transactions.findStatement(5L, 100)).thenReturn(List.of());

        orchestrator.statement(5L, 5_000, ISHA);

        verify(transactions).findStatement(5L, 100);
    }

    @Test
    void adminSkipsOwnershipCheckAndLimitHasFloorOfOne() {
        when(transactions.findStatement(5L, 1)).thenReturn(List.of());

        orchestrator.statement(5L, -3, ADMIN);

        verify(accountClient, never()).owner(anyLong());
        verify(transactions).findStatement(5L, 1);
    }
}
