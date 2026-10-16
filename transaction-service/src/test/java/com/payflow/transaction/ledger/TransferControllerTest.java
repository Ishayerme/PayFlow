package com.payflow.transaction.ledger;

import com.payflow.common.error.ApiException;
import com.payflow.common.error.GlobalExceptionHandler;
import com.payflow.common.security.AuthUser;
import com.payflow.common.security.JwtService;
import com.payflow.common.security.Role;
import com.payflow.transaction.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
@Import({SecurityConfig.class, JwtService.class, GlobalExceptionHandler.class})
class TransferControllerTest {

    private static final AuthUser ISHA = new AuthUser(100L, "isha@example.com", Role.CUSTOMER);

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @MockBean TransferOrchestrator orchestrator;

    private String bearer() {
        return "Bearer " + jwtService.generateToken(ISHA);
    }

    @Test
    void transferWithoutTokenIs401() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":10}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orchestrator);
    }

    @Test
    void validTransferReturns201WithCompletedStatus() throws Exception {
        TransactionRecord record = new TransactionRecord(1L, 2L, new BigDecimal("10.00"), 100L);
        record.complete();
        when(orchestrator.transfer(eq(1L), eq(2L), any(), eq(ISHA))).thenReturn(record);

        mvc.perform(post("/api/transfers").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":10.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.reference").isNotEmpty());
    }

    @Test
    void missingFieldsAndBadAmountAre400() throws Exception {
        mvc.perform(post("/api/transfers").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":1,\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("amount: must be greater than 0; toAccountId: must not be null"));
        verifyNoInteractions(orchestrator);
    }

    @Test
    void insufficientFundsSurfacesAs409() throws Exception {
        when(orchestrator.transfer(any(), any(), any(), any()))
                .thenThrow(ApiException.conflict("Insufficient funds in account PF1"));

        mvc.perform(post("/api/transfers").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Insufficient funds in account PF1"));
    }

    @Test
    void statementReturnsTransactions() throws Exception {
        when(orchestrator.statement(1L, 20, ISHA))
                .thenReturn(List.of(new TransactionRecord(1L, 2L, new BigDecimal("5.00"), 100L)));

        mvc.perform(get("/api/accounts/1/statement").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(5.00))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }
}
