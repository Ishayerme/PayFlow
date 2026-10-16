package com.payflow.notification;

import com.payflow.common.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public record CreateRequest(@NotNull Long userId, @NotBlank @Size(max = 500) String message) {
    }

    public record NotificationResponse(Long id, String message, boolean read, Instant createdAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getMessage(), n.isRead(), n.getCreatedAt());
        }
    }

    /** Called by transaction-service only (requires X-Internal-Key). */
    @PostMapping("/internal/notifications")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse create(@Valid @RequestBody CreateRequest request) {
        return NotificationResponse.from(notificationService.create(request.userId(), request.message()));
    }

    @GetMapping("/api/notifications")
    public List<NotificationResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return notificationService.latestFor(user).stream().map(NotificationResponse::from).toList();
    }

    @PatchMapping("/api/notifications/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return NotificationResponse.from(notificationService.markRead(id, user));
    }
}
