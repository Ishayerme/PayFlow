package com.payflow.transaction.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.ApiException;
import com.payflow.common.security.InternalKeyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;

/** Calls the account-service's /internal API over HTTP. */
@Component
public class AccountClient {

    public record TransferCommand(Long fromAccountId, Long toAccountId, BigDecimal amount, Long requestedBy) {
    }

    public record TransferResult(Long fromAccountId, Long fromOwnerId, BigDecimal fromBalance,
                                 Long toAccountId, Long toOwnerId, BigDecimal toBalance) {
    }

    public record AccountOwner(Long accountId, Long userId) {
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AccountClient(RestClient.Builder builder, ObjectMapper objectMapper,
                         @Value("${payflow.services.account-url}") String baseUrl,
                         @Value("${payflow.internal-key}") String internalKey) {
        this.restClient = builder.baseUrl(baseUrl).defaultHeader(InternalKeyFilter.HEADER, internalKey).build();
        this.objectMapper = objectMapper;
    }

    public TransferResult transfer(TransferCommand command) {
        return call(() -> restClient.post().uri("/internal/transfers").body(command)
                .retrieve().body(TransferResult.class));
    }

    public AccountOwner owner(Long accountId) {
        return call(() -> restClient.get().uri("/internal/accounts/{id}/owner", accountId)
                .retrieve().body(AccountOwner.class));
    }

    private interface RemoteCall<T> {
        T run();
    }

    /**
     * Translates remote failures into ApiExceptions:
     * a 4xx from account-service keeps its status and message (e.g. 409 "Insufficient funds"),
     * anything else (5xx, connection refused) becomes 503 Service Unavailable.
     */
    private <T> T call(RemoteCall<T> remoteCall) {
        try {
            return remoteCall.run();
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                throw new ApiException(status, errorMessage(e));
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Account service error");
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Account service unavailable");
        }
    }

    private String errorMessage(RestClientResponseException e) {
        try {
            JsonNode body = objectMapper.readTree(e.getResponseBodyAsString());
            if (body.hasNonNull("error")) {
                return body.get("error").asText();
            }
        } catch (Exception ignored) {
            // body wasn't our JSON error format; fall back below
        }
        return e.getStatusText();
    }
}
