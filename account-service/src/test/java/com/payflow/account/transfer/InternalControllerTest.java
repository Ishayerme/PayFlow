package com.payflow.account.transfer;

import com.payflow.account.WebLayerTestBase;
import com.payflow.account.account.Account;
import com.payflow.account.account.AccountService;
import com.payflow.account.transfer.TransferService.TransferResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalController.class)
class InternalControllerTest extends WebLayerTestBase {

    private static final String BODY =
            "{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":25.00,\"requestedBy\":100}";

    @MockBean TransferService transferService;
    @MockBean AccountService accountService;

    @Test
    void transferWithoutInternalKeyIsRejectedEvenWithValidUserToken() throws Exception {
        // A logged-in customer must not be able to call the internal API directly.
        mvc.perform(post("/internal/transfers").header("Authorization", bearer(CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(transferService);
    }

    @Test
    void transferWithInternalKeyIsExecuted() throws Exception {
        when(transferService.transfer(1L, 2L, new BigDecimal("25.00"), 100L)).thenReturn(
                new TransferResult(1L, 100L, new BigDecimal("75.00"), 2L, 200L, new BigDecimal("25.00")));

        mvc.perform(post("/internal/transfers").header("X-Internal-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toOwnerId").value(200));
    }

    @Test
    void ownerLookupReturnsAccountOwner() throws Exception {
        Account account = new Account(42L, "PF0000000007");
        ReflectionTestUtils.setField(account, "id", 7L);
        when(accountService.find(7L)).thenReturn(account);

        mvc.perform(get("/internal/accounts/7/owner").header("X-Internal-Key", "test-internal-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42));
    }
}
