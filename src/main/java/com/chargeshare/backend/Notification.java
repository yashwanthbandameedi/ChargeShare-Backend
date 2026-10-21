
package com.chargeshare.backend;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipientEmail;

    @Column(nullable = false)
    private String recipientRole;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    private Long relatedBookingId;

    @Column(name = "is_read", nullable = false)
private boolean read = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String value) { recipientEmail = value; }

    public String getRecipientRole() { return recipientRole; }
    public void setRecipientRole(String value) { recipientRole = value; }

    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }

    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }

    public Long getRelatedBookingId() { return relatedBookingId; }
    public void setRelatedBookingId(Long value) { relatedBookingId = value; }

    public boolean isRead() { return read; }
    public void setRead(boolean value) { read = value; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
