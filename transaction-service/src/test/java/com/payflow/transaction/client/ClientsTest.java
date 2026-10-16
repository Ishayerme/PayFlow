package com.payflow.transaction.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.ApiException;
import com.payflow.transaction.client.AccountClient.TransferCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** HTTP clients tested against a fake server: no other service needs to be running. */
class ClientsTest {

    private static final TransferCommand COMMAND = new TransferCommand(1L, 2L, new BigDecimal("10.00"), 100L);

    private MockRestServiceServer server;              // fake account-service
    private MockRestServiceServer notificationServer;  // fake notification-service
    private AccountClient accountClient;
    private NotificationClient notificationClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder accountBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(accountBuilder).build();
        accountClient = new AccountClient(accountBuilder, new ObjectMapper(), "http://account.test", "k3y");

        RestClient.Builder notificationBuilder = RestClient.builder();
        notificationServer = MockRestServiceServer.bindTo(notificationBuilder).build();
        notificationClient = new NotificationClient(notificationBuilder, "http://notification.test", "k3y");
    }

    @Test
    void transferSendsInternalKeyAndParsesResult() {
        server.expect(requestTo("http://account.test/internal/transfers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Key", "k3y"))
                .andRespond(withSuccess("""
                        {"fromAccountId":1,"fromOwnerId":100,"fromBalance":90.00,
                         "toAccountId":2,"toOwnerId":200,"toBalance":10.00}""", MediaType.APPLICATION_JSON));

        var result = accountClient.transfer(COMMAND);

        assertThat(result.toOwnerId()).isEqualTo(200L);
        assertThat(result.fromBalance()).isEqualByComparingTo("90.00");
        server.verify();
    }

    @Test
    void businessRejectionKeepsStatusAndMessage() {
        server.expect(requestTo("http://account.test/internal/transfers"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":409,\"error\":\"Insufficient funds in account PF1\"}"));

        assertThatThrownBy(() -> accountClient.transfer(COMMAND))
                .isInstanceOf(ApiException.class)
                .hasMessage("Insufficient funds in account PF1")
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void serverErrorBecomesServiceUnavailable() {
        server.expect(requestTo("http://account.test/internal/accounts/7/owner")).andRespond(withServerError());

        assertThatThrownBy(() -> accountClient.owner(7L))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void ownerLookupParsesUserId() {
        server.expect(requestTo("http://account.test/internal/accounts/7/owner"))
                .andRespond(withSuccess("{\"accountId\":7,\"userId\":42}", MediaType.APPLICATION_JSON));

        assertThat(accountClient.owner(7L).userId()).isEqualTo(42L);
    }

    @Test
    void notificationFailureIsSwallowedSoTransfersNeverFailBecauseOfIt() {
        notificationServer.expect(requestTo("http://notification.test/internal/notifications"))
                .andRespond(withServerError());

        assertThatCode(() -> notificationClient.send(100L, "hello")).doesNotThrowAnyException();
    }

    @Test
    void notificationIsPostedWithInternalKey() {
        notificationServer.expect(requestTo("http://notification.test/internal/notifications"))
                .andExpect(header("X-Internal-Key", "k3y"))
                .andRespond(withStatus(HttpStatus.CREATED));

        notificationClient.send(100L, "hello");

        notificationServer.verify();
    }
}
