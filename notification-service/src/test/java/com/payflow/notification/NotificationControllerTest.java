package com.payflow.notification;

import com.payflow.common.error.GlobalExceptionHandler;
import com.payflow.common.security.AuthUser;
import com.payflow.common.security.JwtService;
import com.payflow.common.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, JwtService.class, GlobalExceptionHandler.class})
class NotificationControllerTest {

    private static final AuthUser ISHA = new AuthUser(100L, "isha@example.com", Role.CUSTOMER);

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @MockBean NotificationService notificationService;

    @Test
    void internalCreateNeedsInternalKey() throws Exception {
        mvc.perform(post("/internal/notifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":100,\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationService);
    }

    @Test
    void internalCreateWithKeyReturns201() throws Exception {
        when(notificationService.create(100L, "hi")).thenReturn(new Notification(100L, "hi"));

        mvc.perform(post("/internal/notifications").header("X-Internal-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":100,\"message\":\"hi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("hi"));
    }

    @Test
    void userReadsOwnNotificationsWithToken() throws Exception {
        when(notificationService.latestFor(ISHA)).thenReturn(List.of(new Notification(100L, "Received 5.00")));

        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + jwtService.generateToken(ISHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].message").value("Received 5.00"));
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void markReadReturnsUpdatedNotification() throws Exception {
        Notification read = new Notification(100L, "hi");
        read.markRead();
        when(notificationService.markRead(1L, ISHA)).thenReturn(read);

        mvc.perform(patch("/api/notifications/1/read")
                        .header("Authorization", "Bearer " + jwtService.generateToken(ISHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }
}
