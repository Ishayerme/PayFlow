package com.payflow.account.transfer;

import com.payflow.account.EmbeddedPostgresTest;
import com.payflow.account.account.AccountRepository;
import com.payflow.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stress tests against a real PostgreSQL. These are the tests that prove the
 * locking in TransferService works; mocks can't show this.
 */
@EmbeddedPostgresTest
class TransferConcurrencyTest {

    private static final int THREADS = 32;
    private static final int ACCOUNTS = 10;
    private static final BigDecimal STARTING_BALANCE = new BigDecimal("1000.00");

    @Autowired TransferService transferService;
    @Autowired AccountRepository accountRepository;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> accountIds = new ArrayList<>();
    private final List<Long> ownerIds = new ArrayList<>();

    @BeforeEach
    void createAccounts() {
        jdbc.execute("TRUNCATE accounts, users RESTART IDENTITY CASCADE");
        accountIds.clear();
        ownerIds.clear();
        for (int i = 0; i < ACCOUNTS; i++) {
            Long userId = jdbc.queryForObject(
                    "INSERT INTO users (email, password_hash, full_name, role) VALUES (?, 'x', ?, 'CUSTOMER') RETURNING id",
                    Long.class, "user" + i + "@test.com", "User " + i);
            Long accountId = jdbc.queryForObject(
                    "INSERT INTO accounts (user_id, account_number, balance, status) VALUES (?, ?, ?, 'ACTIVE') RETURNING id",
                    Long.class, userId, "PF%010d".formatted(i), STARTING_BALANCE);
            ownerIds.add(userId);
            accountIds.add(accountId);
        }
    }

    /** Runs every task at the same moment on THREADS threads and waits for all of them. */
    private static List<Future<Void>> runConcurrently(List<Callable<Void>> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(() -> {
                startGate.await();       // hold every thread until all are queued, to maximise contention
                return task.call();
            }));
        }
        startGate.countDown();
        pool.shutdown();
        // A deadlock would show up here as a timeout.
        assertThat(pool.awaitTermination(120, TimeUnit.SECONDS)).as("all transfers finished (no deadlock)").isTrue();
        return futures;
    }

    @Test
    void thousandRandomConcurrentTransfersKeepEveryBalanceCorrect() throws Exception {
        BigDecimal totalBefore = accountRepository.totalBalance();
        Random random = new Random(42);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejectedForFunds = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < 1_000; i++) {
            int from = random.nextInt(ACCOUNTS);
            int to = (from + 1 + random.nextInt(ACCOUNTS - 1)) % ACCOUNTS;   // always a different account
            BigDecimal amount = BigDecimal.valueOf(1 + random.nextInt(300));
            Long fromId = accountIds.get(from);
            Long toId = accountIds.get(to);
            Long owner = ownerIds.get(from);
            tasks.add(() -> {
                try {
                    transferService.transfer(fromId, toId, amount, owner);
                    succeeded.incrementAndGet();
                } catch (ApiException e) {
                    // The only acceptable failure is a clean "insufficient funds" rejection.
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    rejectedForFunds.incrementAndGet();
                }
                return null;
            });
        }

        for (Future<Void> f : runConcurrently(tasks)) {
            f.get();   // rethrows any unexpected exception from a worker thread
        }

        assertThat(succeeded.get() + rejectedForFunds.get()).isEqualTo(1_000);
        assertThat(succeeded.get()).as("most transfers should go through").isGreaterThan(500);
        // Money is only moved, never created or destroyed.
        assertThat(accountRepository.totalBalance()).isEqualByComparingTo(totalBefore);
        assertThat(accountRepository.findAll())
                .allSatisfy(a -> assertThat(a.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO));
    }

    @Test
    void opposingTransfersBetweenSameTwoAccountsDoNotDeadlock() throws Exception {
        // A->B and B->A at the same time is the classic deadlock. Ordered locking prevents it.
        Long a = accountIds.get(0);
        Long b = accountIds.get(1);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            tasks.add(() -> { transferService.transfer(a, b, BigDecimal.ONE, ownerIds.get(0)); return null; });
            tasks.add(() -> { transferService.transfer(b, a, BigDecimal.ONE, ownerIds.get(1)); return null; });
        }

        for (Future<Void> f : runConcurrently(tasks)) {
            f.get();
        }

        // 500 out and 500 back: both end exactly where they started.
        assertThat(accountRepository.findById(a).orElseThrow().getBalance()).isEqualByComparingTo(STARTING_BALANCE);
        assertThat(accountRepository.findById(b).orElseThrow().getBalance()).isEqualByComparingTo(STARTING_BALANCE);
    }

    @Test
    void concurrentWithdrawalsCannotOverdrawAnAccount() throws Exception {
        // Account 0 holds 1000. 32 threads each try to send 100 at once:
        // exactly 10 can succeed, and the balance must land on exactly 0.
        Long source = accountIds.get(0);
        Long target = accountIds.get(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                try {
                    transferService.transfer(source, target, new BigDecimal("100.00"), ownerIds.get(0));
                    succeeded.incrementAndGet();
                } catch (ApiException ignored) {
                    // insufficient funds: expected for 22 of the 32 attempts
                }
                return null;
            });
        }

        runConcurrently(tasks);

        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(accountRepository.findById(source).orElseThrow().getBalance()).isEqualByComparingTo("0.00");
        assertThat(accountRepository.findById(target).orElseThrow().getBalance()).isEqualByComparingTo("2000.00");
    }
}
