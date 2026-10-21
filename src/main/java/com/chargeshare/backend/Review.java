
package com.chargeshare.backend;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "reviews",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_review_booking",
            columnNames = "booking_id"
        )
    },
    indexes = {
        @Index(name = "idx_review_charger", columnList = "charger_id"),
        @Index(name = "idx_review_host", columnList = "host_id")
    }
)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerAccount customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charger_id", nullable = false)
    private Charger charger;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private HostAccount host;

    @Column(name = "charger_rating", nullable = false)
    private int chargerRating;

    @Column(name = "host_discipline_rating", nullable = false)
    private int hostDisciplineRating;

    @Column(length = 1000)
    private String comment;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Review() {
    }

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public CustomerAccount getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerAccount customer) {
        this.customer = customer;
    }

    public Charger getCharger() {
        return charger;
    }

    public void setCharger(Charger charger) {
        this.charger = charger;
    }

    public HostAccount getHost() {
        return host;
    }

    public void setHost(HostAccount host) {
        this.host = host;
    }

    public int getChargerRating() {
        return chargerRating;
    }

    public void setChargerRating(int chargerRating) {
        this.chargerRating = chargerRating;
    }

    public int getHostDisciplineRating() {
        return hostDisciplineRating;
    }

    public void setHostDisciplineRating(int hostDisciplineRating) {
        this.hostDisciplineRating = hostDisciplineRating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
