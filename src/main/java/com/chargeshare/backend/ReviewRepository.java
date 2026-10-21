
package com.chargeshare.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Check whether a booking already has a review.
    boolean existsByBookingId(Long bookingId);

    // Get reviews for a charger, newest first.
    @Query("""
        SELECT r
        FROM Review r
        JOIN FETCH r.customer
        WHERE r.charger.id = :chargerId
        ORDER BY r.createdAt DESC
        """)
    List<Review> findReviewsForCharger(
            @Param("chargerId") Long chargerId
    );

    // Get reviews for a host, newest first.
    @Query("""
        SELECT r
        FROM Review r
        JOIN FETCH r.customer
        JOIN FETCH r.charger
        WHERE r.host.id = :hostId
        ORDER BY r.createdAt DESC
        """)
    List<Review> findReviewsForHost(
            @Param("hostId") Long hostId
    );

    // Calculate average charger rating and review count.
    @Query("""
        SELECT AVG(r.chargerRating)
        FROM Review r
        WHERE r.charger.id = :chargerId
        """)
    Double getAverageChargerRating(
            @Param("chargerId") Long chargerId
    );

    // Calculate average host discipline rating and review count.
    @Query("""
        SELECT AVG(r.hostDisciplineRating)
        FROM Review r
        WHERE r.host.id = :hostId
        """)
    Double getAverageHostDisciplineRating(
            @Param("hostId") Long hostId
    );

    // Check whether the customer has reviewed a particular booking.
    @Query("""
        SELECT r
        FROM Review r
        WHERE r.booking.id = :bookingId
          AND LOWER(r.customer.email) = LOWER(:email)
        """)
    Optional<Review> findCustomerReviewForBooking(
            @Param("bookingId") Long bookingId,
            @Param("email") String email
    );
}
