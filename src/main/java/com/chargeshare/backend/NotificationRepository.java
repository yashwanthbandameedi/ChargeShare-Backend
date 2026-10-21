
package com.chargeshare.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientEmailIgnoreCaseOrderByCreatedAtDesc(
            String recipientEmail);

    Optional<Notification> findByIdAndRecipientEmailIgnoreCase(
            Long id, String recipientEmail);

    long countByRecipientEmailIgnoreCaseAndReadFalse(
            String recipientEmail);
}
