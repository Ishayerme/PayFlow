package com.payflow.account.account;

import com.payflow.common.error.ApiException;
import com.payflow.common.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;

@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public Account open(AuthUser owner) {
        String number;
        do {
            number = "PF" + String.format("%010d", RANDOM.nextLong(10_000_000_000L));
        } while (accounts.existsByAccountNumber(number));
        return accounts.save(new Account(owner.id(), number));
    }

    @Transactional(readOnly = true)
    public List<Account> listOwn(AuthUser user) {
        return accounts.findByUserIdOrderByIdAsc(user.id());
    }

    @Transactional(readOnly = true)
    public List<Account> listAll() {
        return accounts.findAll();
    }

    @Transactional(readOnly = true)
    public Account get(Long id, AuthUser user) {
        Account account = find(id);
        requireOwnerOrAdmin(account, user);
        return account;
    }

    /** Deposits lock the row too, so a deposit can't overwrite a concurrent transfer. */
    @Transactional
    public Account deposit(Long id, BigDecimal amount, AuthUser user) {
        Account account = accounts.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Account " + id + " not found"));
        requireOwnerOrAdmin(account, user);
        account.deposit(amount);
        return account;
    }

    @Transactional
    public Account setStatus(Long id, AccountStatus status) {
        Account account = accounts.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Account " + id + " not found"));
        account.setStatus(status);
        return account;
    }

    @Transactional(readOnly = true)
    public Account find(Long id) {
        return accounts.findById(id).orElseThrow(() -> ApiException.notFound("Account " + id + " not found"));
    }

    private static void requireOwnerOrAdmin(Account account, AuthUser user) {
        if (!user.isAdmin() && !account.getUserId().equals(user.id())) {
            // 404 rather than 403, so customers can't probe which account ids exist.
            throw ApiException.notFound("Account " + account.getId() + " not found");
        }
    }
}
