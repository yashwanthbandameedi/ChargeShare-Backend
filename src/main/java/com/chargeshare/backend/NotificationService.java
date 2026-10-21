
package com.chargeshare.backend;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(
            NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notifyUser(
            String email,
            String role,
            String title,
            String message,
            Long bookingId) {

        if (email == null || email.isBlank()) {
            return;
        }

        Notification notification = new Notification();
        notification.setRecipientEmail(email.trim());
        notification.setRecipientRole(role);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRelatedBookingId(bookingId);

        notificationRepository.save(notification);
    }
}
