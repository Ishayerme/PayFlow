package com.payflow.transaction.ledger;

import com.payflow.common.error.ApiException;
import com.payflow.common.security.AuthUser;
import com.payflow.transaction.client.AccountClient;
import com.payflow.transaction.client.AccountClient.TransferCommand;
import com.payflow.transaction.client.AccountClient.TransferResult;
import com.payflow.transaction.client.NotificationClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Coordinates one transfer across the three services:
 *
 *   0. check the caller owns the sending account
 *   1. record it as PENDING in the ledger  (so every attempt is auditable)
 *   2. ask account-service to move the money (the locked, atomic part)
 *   3. mark it COMPLETED or FAILED with the reason
 *   4. notify sender and receiver (best effort)
 *
 * Deliberately NOT one big @Transactional: the PENDING row is committed before
 * the remote call, so even a crash mid-transfer leaves a trace to investigate.
 */
@Service
public class TransferOrchestrator {

    private static final int MAX_STATEMENT_ROWS = 100;

    private final TransactionRepository transactions;
    private final AccountClient accountClient;
    private final NotificationClient notificationClient;

    public TransferOrchestrator(TransactionRepository transactions, AccountClient accountClient,
                                NotificationClient notificationClient) {
        this.transactions = transactions;
        this.accountClient = accountClient;
        this.notificationClient = notificationClient;
    }

    public TransactionRecord transfer(Long fromAccountId, Long toAccountId, BigDecimal amount, AuthUser user) {
        // Reject someone else's account up front, so their attempt never appears
        // in the real owner's statement. account-service re-checks under the row lock.
        if (!accountClient.owner(fromAccountId).userId().equals(user.id())) {
            throw ApiException.forbidden("You can only send money from your own account");
        }
        TransactionRecord record = transactions.save(
                new TransactionRecord(fromAccountId, toAccountId, amount, user.id()));
        try {
            TransferResult result = accountClient.transfer(
                    new TransferCommand(fromAccountId, toAccountId, amount, user.id()));
            record.complete();
            transactions.save(record);
            notificationClient.send(result.fromOwnerId(),
                    "Sent " + amount + " from account " + fromAccountId + ". New balance: " + result.fromBalance());
            notificationClient.send(result.toOwnerId(),
                    "Received " + amount + " into account " + toAccountId + ". New balance: " + result.toBalance());
            return record;
        } catch (ApiException e) {
            record.fail(e.getMessage());
            transactions.save(record);
            notificationClient.send(user.id(), "Transfer of " + amount + " failed: " + e.getMessage());
            throw e;
        }
    }

    public List<TransactionRecord> statement(Long accountId, int limit, AuthUser user) {
        if (!user.isAdmin()) {
            Long ownerId = accountClient.owner(accountId).userId();
            if (!ownerId.equals(user.id())) {
                throw ApiException.notFound("Account " + accountId + " not found");
            }
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_STATEMENT_ROWS));
        return transactions.findStatement(accountId, safeLimit);
    }
}
