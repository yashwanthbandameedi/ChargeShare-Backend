
package com.chargeshare.backend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = {
    "http://localhost:5173",
    "http://localhost:5174",
    "http://localhost:5175",
    "http://localhost:5176"
})
public class NotificationController {

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getNotifications(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = authentication.getName();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notifications",
                repository.findByRecipientEmailIgnoreCaseOrderByCreatedAtDesc(email));
        result.put("unreadCount",
                repository.countByRecipientEmailIgnoreCaseAndReadFalse(email));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/read")
    @Transactional
    public ResponseEntity<?> markAsRead(
            @PathVariable Long id,
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return repository.findByIdAndRecipientEmailIgnoreCase(
                id, authentication.getName())
            .map(notification -> {
                notification.setRead(true);
                repository.save(notification);
                return ResponseEntity.ok(notification);
            })
            .orElseGet(() ->
                ResponseEntity.notFound().build());
    }

    @PatchMapping("/read-all")
    @Transactional
    public ResponseEntity<?> markAllAsRead(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var notifications =
                repository.findByRecipientEmailIgnoreCaseOrderByCreatedAtDesc(
                        authentication.getName());

        notifications.forEach(notification -> notification.setRead(true));
        repository.saveAll(notifications);

        return ResponseEntity.ok(Map.of(
                "message", "All notifications marked as read."
        ));
    }
}
