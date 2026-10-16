package com.payflow.notification;

import com.payflow.common.error.ApiException;
import com.payflow.common.security.AuthUser;
import com.payflow.common.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final AuthUser ISHA = new AuthUser(100L, "isha@example.com", Role.CUSTOMER);

    @Mock NotificationRepository notifications;
    @InjectMocks NotificationService notificationService;

    @Test
    void createStoresUnreadNotification() {
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification n = notificationService.create(100L, "Received 10.00");

        assertThat(n.getUserId()).isEqualTo(100L);
        assertThat(n.isRead()).isFalse();
    }

    @Test
    void latestReturnsOnlyCallersNotifications() {
        when(notifications.findTop50ByUserIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of(new Notification(100L, "hi")));

        assertThat(notificationService.latestFor(ISHA)).hasSize(1);
    }

    @Test
    void ownerCanMarkNotificationRead() {
        when(notifications.findById(1L)).thenReturn(Optional.of(new Notification(100L, "hi")));

        assertThat(notificationService.markRead(1L, ISHA).isRead()).isTrue();
    }

    @Test
    void cannotMarkSomeoneElsesNotificationRead() {
        when(notifications.findById(1L)).thenReturn(Optional.of(new Notification(555L, "not yours")));

        assertThatThrownBy(() -> notificationService.markRead(1L, ISHA))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }
}
