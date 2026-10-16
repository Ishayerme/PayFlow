# PayFlow: Concurrent Payments Backend

PayFlow is a small payments system built as **three Spring Boot microservices** with a React dashboard.
Its main job is to move money between accounts **correctly when many transfers happen at the same time**.

- 86 automated tests (JUnit 5 + Mockito), with an **80% line-coverage gate** (JaCoCo) that fails the build if coverage drops
- Stress-tested with **1,000 concurrent transfers across 32 threads** on a real PostgreSQL
- JWT login with customer / admin roles, BCrypt password hashing, request validation, parameterized queries
- Statement queries proven to use their indexes with `EXPLAIN ANALYZE`

---

## 1. Architecture

```
                         Browser (React dashboard, port 5173 / 8080)
                                          |
           +------------------------------+------------------------------+
           |                              |                              |
   /api/auth, /api/accounts      /api/transfers, statements       /api/notifications
           |                              |                              |
  +--------v---------+          +---------v----------+          +--------v-----------+
  | account-service  |<---------| transaction-service|--------->|notification-service|
  |      :8081       | internal |       :8082        | internal |       :8083        |
  | users, login,    |  HTTP    | public transfer    |  HTTP    | stores messages    |
  | accounts,        |          | API, ledger,       |          | per user           |
  | LOCKED TRANSFER  |          | statements         |          |                    |
  +--------+---------+          +---------+----------+          +--------+-----------+
           |                              |                              |
   payflow_accounts DB          payflow_transactions DB        payflow_notifications DB
                         (each service owns its own database)
```

**What happens when Isha sends ₹120 to Ravi:**

1. Browser → `POST /api/transfers` on **transaction-service**, with her JWT.
2. transaction-service checks that she owns the sending account (asks account-service).
3. It writes a **PENDING** row in its ledger, so every attempt is recorded.
4. It calls account-service `POST /internal/transfers`. **This is where money actually moves**, inside one database transaction with row locks.
5. The ledger row becomes **COMPLETED**, or **FAILED** with the reason (e.g. insufficient funds).
6. It asks notification-service to notify both people. If notifications are down, the transfer still succeeds (best effort).

`/internal/**` endpoints can only be called by the other services: they need a shared secret in the `X-Internal-Key` header. A user's JWT is not enough.

---

## 2. Run it locally (Windows, no Docker needed)

Needs: **JDK 21** and **Node 18+**. Maven comes with the project (`mvnw`).

```bat
:: 1. Start PostgreSQL (embedded; keep this window open)
mvnw.cmd -pl dev-db -am install -DskipTests
mvnw.cmd -pl dev-db exec:java

:: 2. In three more windows, start the services
java -jar account-service\target\account-service-1.0.0.jar
java -jar transaction-service\target\transaction-service-1.0.0.jar
java -jar notification-service\target\notification-service-1.0.0.jar

:: 3. Dashboard
cd frontend
npm install
npm run dev          :: open http://localhost:5173
```

(Build the jars first with `mvnw.cmd install -DskipTests` if `target\` is empty.)

Default admin login (change it via `ADMIN_EMAIL` / `ADMIN_PASSWORD`): `admin@payflow.local` / `Admin@12345`.

**With Docker** (whole stack): copy `.env.example` to `.env`, set the values, then `docker compose up --build` and open http://localhost:8080.

## 3. Run the tests

```bat
mvnw.cmd verify
```

This runs all 86 tests and then the JaCoCo coverage check. If any module is below 80% line coverage, **the build fails**.
Coverage report: `account-service\target\site\jacoco\index.html` (same for each module).

The database tests start a **real PostgreSQL inside the test** (Zonky embedded-postgres), so no install or Docker is needed.

---

## 4. The important ideas, explained

### 4.1 The race condition, and how row locks fix it

Without locking, two transfers from the same account can both read the old balance:

```
Balance = 100
Thread A: read balance (100)             Thread B: read balance (100)
Thread A: 100 - 80 = 20, save 20         Thread B: 100 - 80 = 20, save 20
Result: 160 was sent, but only 80 was taken from the account.  Money was created.
```

**Fix:** `AccountRepository.findByIdForUpdate` runs `SELECT ... FOR UPDATE`. This takes a **row-level lock**:
the second transaction **waits** until the first commits, then reads the new balance (20) and correctly fails with "insufficient funds".

File: `account-service/.../transfer/TransferService.java`

### 4.2 Deadlock, and how lock ordering prevents it

Transfer 1 is A→B and transfer 2 is B→A, at the same moment:

```
T1 locks A, then wants B      T2 locks B, then wants A
→ each waits for the other forever = DEADLOCK
```

**Fix:** always lock the **lower account id first**. Both transfers then try to lock A first; one gets it,
the other waits, and no circular wait can form. That's `Math.min / Math.max` in `TransferService`.

### 4.3 How we *proved* it works

`TransferConcurrencyTest` runs against a real PostgreSQL:

| Test | What it does | What must hold |
|---|---|---|
| 1,000 random transfers | 32 threads, 10 accounts, random amounts | Total money unchanged; no balance below 0 |
| Opposing transfers | 500 × A→B and 500 × B→A at once | Finishes (no deadlock); both end where they started |
| Overdraw race | 32 threads each send ₹100 from an account holding ₹1,000 | Exactly 10 succeed; balance ends at exactly 0 |

**We checked that the test can actually fail:** with the lock removed (`findById` instead of `findByIdForUpdate`),
**all three tests fail**. A test that can't fail proves nothing.

### 4.4 Security

| Feature | Where | Why |
|---|---|---|
| **JWT** (HS256) | `common/.../JwtService.java` | Stateless login. The token is signed; changing anything in it (e.g. role → ADMIN) breaks the signature. A test does exactly that. |
| **Roles** | `SecurityConfig`: `/api/admin/**` needs `ROLE_ADMIN` | Customers get 403 on admin endpoints |
| **Ownership checks** | `AccountService`, `TransferService`, `TransferOrchestrator` | You can't read or spend from someone else's account (they get 404/403) |
| **BCrypt** | `SecurityConfig.passwordEncoder()` | Passwords stored as salted, slow hashes, never as plain text |
| **Validation** | `@Valid`, `@Positive`, `@Digits(fraction = 2)` on request records | Bad input is rejected with 400 before reaching business code |
| **Parameterized queries** | Spring Data JPA + named parameters in `TransactionRepository` | User input is always bound as `?`, never pasted into SQL, so no SQL injection |
| **Internal key** | `common/.../InternalKeyFilter.java` | Only our services can call `/internal/**`; compared in constant time |
| **DB constraint** | `CHECK (balance >= 0)` in the Flyway migration | Even buggy code can't store a negative balance |
| **Same login error** | `AuthService.login` | "Invalid email or password" for both cases, so attackers can't discover registered emails |

### 4.5 Indexes and EXPLAIN ANALYZE

A statement = "latest transactions where this account sent **or** received money". The migration creates
`(from_account_id, created_at DESC)` and `(to_account_id, created_at DESC)` indexes, and the query is written as
two halves joined with `UNION ALL`, so each half can use its own index.

`StatementQueryPlanTest` loads 50,000 rows, runs `EXPLAIN ANALYZE` and asserts both indexes are used with no `Seq Scan`.
Measured plan:

```
Limit (actual time=0.063..0.148 rows=20)
  -> Merge Append
       -> Index Scan using idx_transactions_from_created on transactions
       -> Index Scan using idx_transactions_to_created on transactions
```

### 4.6 Testing layers

| Kind | Tool | Example |
|---|---|---|
| Unit tests | JUnit 5 + **Mockito** (fake repositories/clients) | `TransferServiceTest`: insufficient funds, lock order, ownership |
| Web layer | `@WebMvcTest` + MockMvc, real security filters | `AccountControllerTest`: 401 without token, 403 for customers on admin URLs |
| HTTP clients | `MockRestServiceServer` | `ClientsTest`: a 409 from account-service keeps its message |
| Database / concurrency | `@SpringBootTest` + embedded PostgreSQL | `TransferConcurrencyTest`, `StatementQueryPlanTest` |
| Full HTTP | Real server on a random port | `FullHttpSecurityTest` (see bug 2 below) |

### 4.7 Bugs found during development (good interview stories)

1. **Flaky JWT test.** The "tampered token" test flipped the last character of the signature. In base64 the last
   character partly holds padding bits, so sometimes the decoded bytes didn't change and the forged token was accepted
   by the *test*, not the code. Fixed by editing the payload (CUSTOMER → ADMIN) instead, which is what an attacker would do.
2. **403 showing up as 401.** In the running app, Spring forwards errors to `/error`; that page required login, so a
   customer hitting an admin URL got 401 instead of 403. Mock-based tests didn't catch it; a manual end-to-end run did.
   Fixed by permitting `/error`, and added `FullHttpSecurityTest` so it can't come back.
3. **Someone else's failed attempt in your statement.** If Ravi tried to send from Isha's account, the rejected attempt
   was recorded in *Isha's* ledger. Fixed by checking ownership before recording anything.

---

## 5. Project layout

```
common/                 JWT service + filter, internal-key filter, error handler (shared by all services)
account-service/        users, login, accounts, deposits, the locked transfer      (port 8081)
transaction-service/    public transfer API, ledger, statements                    (port 8082)
notification-service/   per-user notifications                                     (port 8083)
dev-db/                 starts PostgreSQL locally without installing it
frontend/               React dashboard (Vite)
docker-compose.yml      whole stack in containers
```

Each service's database schema is in `src/main/resources/db/migration/` (Flyway runs it on start-up).

## 6. Main API

| Method | Path | Who |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | anyone |
| GET / POST | `/api/accounts` | logged in: list / open own accounts |
| POST | `/api/accounts/{id}/deposit` | account owner |
| POST | `/api/transfers` | account owner |
| GET | `/api/accounts/{id}/statement?limit=20` | account owner or admin |
| GET | `/api/notifications` | logged in |
| GET | `/api/admin/accounts` | admin |
| PATCH | `/api/admin/accounts/{id}/status` | admin (freeze / unfreeze) |

## 7. Honest limits (and what you'd do next)

- **Distributed transaction:** money moves inside account-service's single DB transaction (safe), but the ledger
  update in transaction-service is a separate step. If transaction-service crashed right after the money moved, the
  ledger row would stay PENDING. Next step: an outbox/event (e.g. Kafka) or a reconciliation job for stale PENDING rows.
- **Idempotency:** a client retrying the same request could send money twice. Next step: an `Idempotency-Key` header.
- **Scale:** row locks serialize transfers on the *same* account. That's correct, but a very "hot" account would queue.
- The Docker setup is written but was developed and tested with the local (non-Docker) run above.
