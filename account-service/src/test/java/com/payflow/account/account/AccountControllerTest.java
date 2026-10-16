package com.payflow.account.account;

import com.payflow.account.WebLayerTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest extends WebLayerTestBase {

    @MockBean AccountService accountService;

    private static Account account(Long userId, String balance) {
        Account account = new Account(userId, "PF0000000001");
        account.deposit(new BigDecimal(balance));
        return account;
    }

    @Test
    void requestWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        verifyNoInteractions(accountService);
    }

    @Test
    void requestWithForgedTokenIs401() throws Exception {
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer forged.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerSeesOwnAccounts() throws Exception {
        when(accountService.listOwn(CUSTOMER)).thenReturn(List.of(account(1L, "250.00")));

        mvc.perform(get("/api/accounts").header("Authorization", bearer(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].balance").value(250.00))
                .andExpect(jsonPath("$[0].accountNumber").value("PF0000000001"));
    }

    @Test
    void customerIsForbiddenFromAdminEndpoints() throws Exception {
        mvc.perform(get("/api/admin/accounts").header("Authorization", bearer(CUSTOMER)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accountService);
    }

    @Test
    void adminCanListAndFreezeAccounts() throws Exception {
        Account frozen = account(1L, "10.00");
        frozen.setStatus(AccountStatus.FROZEN);
        when(accountService.listAll()).thenReturn(List.of(account(1L, "10.00")));
        when(accountService.setStatus(5L, AccountStatus.FROZEN)).thenReturn(frozen);

        mvc.perform(get("/api/admin/accounts").header("Authorization", bearer(ADMIN)))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/admin/accounts/5/status").header("Authorization", bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FROZEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
    }

    @Test
    void depositOfNegativeOrOverPreciseAmountIs400() throws Exception {
        mvc.perform(post("/api/accounts/5/deposit").header("Authorization", bearer(CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":-10}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/accounts/5/deposit").header("Authorization", bearer(CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10.999}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(accountService);
    }

    @Test
    void validDepositAndOpenAndGetSucceed() throws Exception {
        when(accountService.deposit(eq(5L), any(), eq(CUSTOMER))).thenReturn(account(1L, "110.00"));
        when(accountService.open(CUSTOMER)).thenReturn(account(1L, "0.00"));
        when(accountService.get(5L, CUSTOMER)).thenReturn(account(1L, "110.00"));

        mvc.perform(post("/api/accounts/5/deposit").header("Authorization", bearer(CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(110.00));
        mvc.perform(post("/api/accounts").header("Authorization", bearer(CUSTOMER)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/accounts/5").header("Authorization", bearer(CUSTOMER)))
                .andExpect(status().isOk());
    }
}
