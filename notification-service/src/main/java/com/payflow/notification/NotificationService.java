package com.payflow.notification;

import com.payflow.common.error.ApiException;
import com.payflow.common.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional
    public Notification create(Long userId, String message) {
        return notifications.save(new Notification(userId, message));
    }

    @Transactional(readOnly = true)
    public List<Notification> latestFor(AuthUser user) {
        return notifications.findTop50ByUserIdOrderByCreatedAtDesc(user.id());
    }

    @Transactional
    public Notification markRead(Long id, AuthUser user) {
        Notification notification = notifications.findById(id)
                .filter(n -> n.getUserId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Notification " + id + " not found"));
        notification.markRead();
        return notification;
    }
}
