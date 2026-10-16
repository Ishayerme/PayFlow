package com.payflow.transaction.ledger;

import com.payflow.common.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class TransferController {

    private final TransferOrchestrator orchestrator;

    public TransferController(TransferOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public record TransferRequest(
            @NotNull Long fromAccountId,
            @NotNull Long toAccountId,
            @NotNull @Positive @DecimalMax("1000000.00") @Digits(integer = 17, fraction = 2) BigDecimal amount) {
    }

    public record TransactionResponse(Long id, UUID reference, Long fromAccountId, Long toAccountId,
                                      BigDecimal amount, TransactionStatus status, String failureReason,
                                      Instant createdAt) {
        static TransactionResponse from(TransactionRecord t) {
            return new TransactionResponse(t.getId(), t.getReference(), t.getFromAccountId(), t.getToAccountId(),
                    t.getAmount(), t.getStatus(), t.getFailureReason(), t.getCreatedAt());
        }
    }

    @PostMapping("/api/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(@Valid @RequestBody TransferRequest request,
                                        @AuthenticationPrincipal AuthUser user) {
        return TransactionResponse.from(
                orchestrator.transfer(request.fromAccountId(), request.toAccountId(), request.amount(), user));
    }

    @GetMapping("/api/accounts/{accountId}/statement")
    public List<TransactionResponse> statement(@PathVariable Long accountId,
                                               @RequestParam(defaultValue = "20") int limit,
                                               @AuthenticationPrincipal AuthUser user) {
        return orchestrator.statement(accountId, limit, user).stream().map(TransactionResponse::from).toList();
    }
}
