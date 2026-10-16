package com.payflow.account.account;

import com.payflow.account.account.AccountDtos.AccountResponse;
import com.payflow.account.account.AccountDtos.AmountRequest;
import com.payflow.account.account.AccountDtos.StatusRequest;
import com.payflow.common.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/api/accounts")
    public List<AccountResponse> myAccounts(@AuthenticationPrincipal AuthUser user) {
        return accountService.listOwn(user).stream().map(AccountResponse::from).toList();
    }

    @PostMapping("/api/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse open(@AuthenticationPrincipal AuthUser user) {
        return AccountResponse.from(accountService.open(user));
    }

    @GetMapping("/api/accounts/{id}")
    public AccountResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return AccountResponse.from(accountService.get(id, user));
    }

    @PostMapping("/api/accounts/{id}/deposit")
    public AccountResponse deposit(@PathVariable Long id, @Valid @RequestBody AmountRequest request,
                                   @AuthenticationPrincipal AuthUser user) {
        return AccountResponse.from(accountService.deposit(id, request.amount(), user));
    }

    // ---- Admin only (enforced in SecurityConfig: /api/admin/** requires ROLE_ADMIN) ----

    @GetMapping("/api/admin/accounts")
    public List<AccountResponse> allAccounts() {
        return accountService.listAll().stream().map(AccountResponse::from).toList();
    }

    @PatchMapping("/api/admin/accounts/{id}/status")
    public AccountResponse setStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return AccountResponse.from(accountService.setStatus(id, request.status()));
    }
}
