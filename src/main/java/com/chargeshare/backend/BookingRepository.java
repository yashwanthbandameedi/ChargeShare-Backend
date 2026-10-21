package com.chargeshare.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Check whether another booking overlaps the requested time.
    // CANCELLED and REJECTED bookings do not block new reservations.
    @Query("""
        SELECT COUNT(b) > 0
        FROM Booking b
        WHERE b.charger.id = :chargerId
          AND b.status NOT IN ('CANCELLED', 'REJECTED')
          AND b.startTime < :requestedEnd
          AND b.endTime > :requestedStart
        """)
    boolean hasOverlappingBooking(
            @Param("chargerId") Long chargerId,
            @Param("requestedStart") LocalDateTime requestedStart,
            @Param("requestedEnd") LocalDateTime requestedEnd
    );

    // Check overlapping bookings while excluding the booking
    // currently being approved.
    @Query("""
        SELECT COUNT(b) > 0
        FROM Booking b
        WHERE b.charger.id = :chargerId
          AND b.id <> :excludedBookingId
          AND b.status NOT IN ('CANCELLED', 'REJECTED')
          AND b.startTime < :requestedEnd
          AND b.endTime > :requestedStart
        """)
    boolean hasOverlappingBookingExcludingId(
            @Param("chargerId") Long chargerId,
            @Param("excludedBookingId") Long excludedBookingId,
            @Param("requestedStart") LocalDateTime requestedStart,
            @Param("requestedEnd") LocalDateTime requestedEnd
    );

    // Find active bookings that overlap a calendar date range.
    // Used by the live charger availability calendar.
    @Query("""
        SELECT b
        FROM Booking b
        WHERE b.charger.id = :chargerId
          AND b.status NOT IN ('CANCELLED', 'REJECTED')
          AND b.startTime < :rangeEnd
          AND b.endTime > :rangeStart
        ORDER BY b.startTime ASC
        """)
    List<Booking> findActiveBookingsInRange(
            @Param("chargerId") Long chargerId,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd
    );

    // Retrieve a customer's booking history.
    @Query("""
        SELECT b
        FROM Booking b
        JOIN FETCH b.customer
        JOIN FETCH b.charger c
        JOIN FETCH c.host
        WHERE LOWER(b.customer.email) = LOWER(:email)
        ORDER BY b.createdAt DESC
        """)
    List<Booking> findBookingsForCustomer(
            @Param("email") String email
    );

    // Retrieve booking requests for the authenticated host.
    @Query("""
        SELECT b
        FROM Booking b
        JOIN FETCH b.customer
        JOIN FETCH b.charger c
        JOIN FETCH c.host
        WHERE LOWER(c.host.email) = LOWER(:email)
        ORDER BY b.createdAt DESC
        """)
    List<Booking> findBookingsForHost(
            @Param("email") String email
    );

    // Retrieve bookings for earnings calculations belonging to a host.
    @Query("""
        SELECT b
        FROM Booking b
        JOIN FETCH b.charger c
        JOIN FETCH c.host h
        WHERE LOWER(h.email) = LOWER(:email)
        ORDER BY b.createdAt DESC
        """)
    List<Booking> findEarningsBookingsForHost(
            @Param("email") String email
    );

    // Retrieve one booking with its customer, charger, and host.
    @Query("""
        SELECT b
        FROM Booking b
        JOIN FETCH b.customer
        JOIN FETCH b.charger c
        JOIN FETCH c.host
        WHERE b.id = :bookingId
        """)
    Optional<Booking> findBookingWithDetails(
            @Param("bookingId") Long bookingId
    );
}
