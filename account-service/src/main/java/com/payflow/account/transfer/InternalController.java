package com.payflow.account.transfer;

import com.payflow.account.account.Account;
import com.payflow.account.account.AccountService;
import com.payflow.account.transfer.TransferService.TransferResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Endpoints only the other PayFlow services may call.
 * The InternalKeyFilter rejects any request without the shared X-Internal-Key.
 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    private final TransferService transferService;
    private final AccountService accountService;

    public InternalController(TransferService transferService, AccountService accountService) {
        this.transferService = transferService;
        this.accountService = accountService;
    }

    public record TransferCommand(@NotNull Long fromAccountId, @NotNull Long toAccountId,
                                  @NotNull @Positive BigDecimal amount, @NotNull Long requestedBy) {
    }

    public record AccountOwner(Long accountId, Long userId) {
    }

    @PostMapping("/transfers")
    public TransferResult transfer(@Valid @RequestBody TransferCommand command) {
        return transferService.transfer(command.fromAccountId(), command.toAccountId(),
                command.amount(), command.requestedBy());
    }

    @GetMapping("/accounts/{id}/owner")
    public AccountOwner owner(@PathVariable Long id) {
        Account account = accountService.find(id);
        return new AccountOwner(account.getId(), account.getUserId());
    }
}
