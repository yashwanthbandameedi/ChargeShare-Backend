
package com.chargeshare.backend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174",
        "http://localhost:5175",
        "http://localhost:5176"
})
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;

    public ReviewController(
            ReviewRepository reviewRepository,
            BookingRepository bookingRepository) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
    }

    // Submit a review for the customer's own completed booking.
    @PostMapping
    @Transactional
    public ResponseEntity<?> submitReview(
            @RequestBody ReviewRequest request,
            Authentication authentication) {

        if (!hasRole(authentication, "ROLE_CUSTOMER")) {
            return error(HttpStatus.FORBIDDEN,
                    "Only logged-in customers can submit reviews.");
        }

        if (request == null || request.getBookingId() == null
                || request.getBookingId() <= 0) {
            return error(HttpStatus.BAD_REQUEST,
                    "A valid booking ID is required.");
        }

        if (!validRating(request.getChargerRating())
                || !validRating(request.getHostDisciplineRating())) {
            return error(HttpStatus.BAD_REQUEST,
                    "Both ratings must be between 1 and 5.");
        }

        String comment = request.getComment();

        if (comment != null && comment.length() > 1000) {
            return error(HttpStatus.BAD_REQUEST,
                    "The comment cannot exceed 1000 characters.");
        }

        Booking booking = bookingRepository
                .findBookingWithDetails(request.getBookingId())
                .orElse(null);

        if (booking == null) {
            return error(HttpStatus.NOT_FOUND, "Booking not found.");
        }

        CustomerAccount customer = booking.getCustomer();

        if (customer == null || customer.getEmail() == null
                || !customer.getEmail().equalsIgnoreCase(
                        authentication.getName())) {
            return error(HttpStatus.FORBIDDEN,
                    "You can review only your own bookings.");
        }

        if (!"APPROVED".equalsIgnoreCase(booking.getStatus())) {
            return error(HttpStatus.CONFLICT,
                    "Only approved bookings can be reviewed.");
        }

        // NEW: Require host verification of the booking payment.
        if (!"VERIFIED".equalsIgnoreCase(
                booking.getPaymentStatus())) {
            return error(HttpStatus.CONFLICT,
                    "You can review this booking only after payment is verified.");
        }

        if (booking.getEndTime() == null
                || booking.getEndTime().isAfter(LocalDateTime.now())) {
            return error(HttpStatus.CONFLICT,
                    "You can review this booking after its scheduled end time.");
        }

        if (reviewRepository.existsByBookingId(booking.getId())) {
            return error(HttpStatus.CONFLICT,
                    "This booking has already been reviewed.");
        }

        Charger charger = booking.getCharger();
        HostAccount host = charger == null ? null : charger.getHost();

        if (charger == null || host == null) {
            return error(HttpStatus.CONFLICT,
                    "The charger or host associated with this booking is unavailable.");
        }

        Review review = new Review();
        review.setBooking(booking);
        review.setCustomer(customer);
        review.setCharger(charger);
        review.setHost(host);
        review.setChargerRating(request.getChargerRating());
        review.setHostDisciplineRating(
                request.getHostDisciplineRating());
        review.setComment(
                comment == null || comment.isBlank()
                        ? null
                        : comment.trim());

        Review saved = reviewRepository.save(review);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(saved));
    }

    // List reviews for a charger.
    @GetMapping("/charger/{chargerId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getChargerReviews(
            @PathVariable Long chargerId) {

        if (chargerId == null || chargerId <= 0) {
            return error(HttpStatus.BAD_REQUEST,
                    "A valid charger ID is required.");
        }

        List<Map<String, Object>> reviews =
                reviewRepository.findReviewsForCharger(chargerId)
                        .stream()
                        .map(this::toResponse)
                        .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chargerId", chargerId);
        result.put("averageChargerRating",
                reviewRepository.getAverageChargerRating(chargerId));
        result.put("reviews", reviews);

        return ResponseEntity.ok(result);
    }

    // List reviews for a host, including the average discipline rating.
    @GetMapping("/host/{hostId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getHostReviews(
            @PathVariable Long hostId) {

        if (hostId == null || hostId <= 0) {
            return error(HttpStatus.BAD_REQUEST,
                    "A valid host ID is required.");
        }

        List<Map<String, Object>> reviews =
                reviewRepository.findReviewsForHost(hostId)
                        .stream()
                        .map(this::toResponse)
                        .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hostId", hostId);
        result.put("averageHostDisciplineRating",
                reviewRepository.getAverageHostDisciplineRating(hostId));
        result.put("reviews", reviews);

        return ResponseEntity.ok(result);
    }

    // Check whether the logged-in customer has reviewed a booking.
    @GetMapping("/booking/{bookingId}/mine")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyBookingReview(
            @PathVariable Long bookingId,
            Authentication authentication) {

        if (!hasRole(authentication, "ROLE_CUSTOMER")) {
            return error(HttpStatus.FORBIDDEN,
                    "Only customers can check their booking reviews.");
        }

        if (bookingId == null || bookingId <= 0) {
            return error(HttpStatus.BAD_REQUEST,
                    "A valid booking ID is required.");
        }

        return reviewRepository.findCustomerReviewForBooking(
                        bookingId, authentication.getName())
                .<ResponseEntity<?>>map(review ->
                        ResponseEntity.ok(toResponse(review)))
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of(
                                        "error",
                                        "No review exists for this booking.")));
    }

    private Map<String, Object> toResponse(Review review) {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("id", review.getId());
        result.put("bookingId", review.getBooking().getId());
        result.put("customerName",
                review.getCustomer().getFullName());
        result.put("chargerId", review.getCharger().getId());
        result.put("chargerName", review.getCharger().getName());
        result.put("hostId", review.getHost().getId());
        result.put("chargerRating", review.getChargerRating());
        result.put("hostDisciplineRating",
                review.getHostDisciplineRating());
        result.put("comment", review.getComment());
        result.put("createdAt", review.getCreatedAt());

        return result;
    }

    private boolean validRating(int rating) {
        return rating >= 1 && rating <= 5;
    }

    private boolean hasRole(
            Authentication authentication,
            String requiredRole) {

        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        requiredRole.equals(authority.getAuthority()));
    }

    private ResponseEntity<Map<String, String>> error(
            HttpStatus status, String message) {

        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message);

        return ResponseEntity.status(status).body(body);
    }

    public static class ReviewRequest {
        private Long bookingId;
        private int chargerRating;
        private int hostDisciplineRating;
        private String comment;

        public Long getBookingId() {
            return bookingId;
        }

        public void setBookingId(Long bookingId) {
            this.bookingId = bookingId;
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
    }
}
