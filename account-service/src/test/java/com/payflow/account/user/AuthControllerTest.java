package com.payflow.account.user;

import com.payflow.account.WebLayerTestBase;
import com.payflow.account.user.AuthDtos.LoginResponse;
import com.payflow.account.user.AuthDtos.UserResponse;
import com.payflow.common.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends WebLayerTestBase {

    @MockBean AuthService authService;

    @Test
    void registerIsPublicAndReturns201() throws Exception {
        when(authService.register(any())).thenReturn(new UserResponse(1L, "isha@example.com", "Isha", Role.CUSTOMER));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"isha@example.com\",\"password\":\"secret123\",\"fullName\":\"Isha\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void registerWithInvalidInputIs400AndNeverReachesService() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"fullName\":\"Isha\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "email: must be a well-formed email address; password: must be 8-72 characters"));
        verifyNoInteractions(authService);
    }

    @Test
    void loginReturnsToken() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("jwt-token",
                new UserResponse(1L, "isha@example.com", "Isha", Role.CUSTOMER)));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"isha@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }
}
