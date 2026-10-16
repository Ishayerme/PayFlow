package com.payflow.account.transfer;

import com.payflow.account.account.Account;
import com.payflow.account.account.AccountRepository;
import com.payflow.common.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Moves money between two accounts safely when many transfers run at once.
 *
 * Two ideas make it correct:
 *
 * 1. ROW LOCKS. Both accounts are loaded with SELECT ... FOR UPDATE inside one
 *    database transaction. A second transfer touching either account waits until
 *    this one commits, so it always sees the up-to-date balance.
 *
 * 2. CONSISTENT LOCK ORDER. Locks are always taken lowest account id first.
 *    Without this, transfer A->B could lock A while B->A locks B, and each would
 *    wait forever for the other (a deadlock). If everyone locks in the same order,
 *    that circular wait can't happen.
 *
 * The @Transactional boundary makes it all-or-nothing: if the debit succeeds but
 * anything after it fails, the whole transfer rolls back.
 */
@Service
public class TransferService {

    private final AccountRepository accounts;

    public TransferService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public record TransferResult(Long fromAccountId, Long fromOwnerId, BigDecimal fromBalance,
                                 Long toAccountId, Long toOwnerId, BigDecimal toBalance) {
    }

    @Transactional
    public TransferResult transfer(Long fromId, Long toId, BigDecimal amount, Long requestedBy) {
        if (fromId.equals(toId)) {
            throw ApiException.badRequest("Cannot transfer to the same account");
        }
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("Amount must be positive");
        }

        // Lock in ascending id order (see class comment).
        Long firstId = Math.min(fromId, toId);
        Long secondId = Math.max(fromId, toId);
        Account first = lock(firstId);
        Account second = lock(secondId);
        Account from = first.getId().equals(fromId) ? first : second;
        Account to = from == first ? second : first;

        // Ownership is checked only after locking, so it can't change underneath us.
        if (requestedBy != null && !from.getUserId().equals(requestedBy)) {
            throw ApiException.forbidden("You can only send money from your own account");
        }

        from.withdraw(amount);   // throws 409 if funds are insufficient or account frozen
        to.deposit(amount);

        // No explicit save needed: JPA writes the changed balances at commit.
        return new TransferResult(from.getId(), from.getUserId(), from.getBalance(),
                to.getId(), to.getUserId(), to.getBalance());
    }

    private Account lock(Long id) {
        return accounts.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Account " + id + " not found"));
    }
}
