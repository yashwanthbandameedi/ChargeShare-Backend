
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
@RequestMapping("/api/bookings")
@CrossOrigin(origins = {
    "http://localhost:5173",
    "http://localhost:5174",
    "http://localhost:5175",
    "http://localhost:5176"
})
public class BookingController {

    private final BookingRepository bookingRepository;
    private final ChargerRepository chargerRepository;
    private final CustomerAccountRepository customerRepository;
    private final NotificationService notificationService;

    public BookingController(
            BookingRepository bookingRepository,
            ChargerRepository chargerRepository,
            CustomerAccountRepository customerRepository,
            NotificationService notificationService) {

        this.bookingRepository = bookingRepository;
        this.chargerRepository = chargerRepository;
        this.customerRepository = customerRepository;
        this.notificationService = notificationService;
    }

    // Create a booking as a logged-in customer.
    @PostMapping
    @Transactional
    public ResponseEntity<?> createBooking(
            @RequestBody BookingRequest request,
            Authentication authentication) {

        if (!hasRole(authentication, "ROLE_CUSTOMER")) {
            return error(HttpStatus.FORBIDDEN,
                    "Only logged-in customers can book chargers.");
        }

        if (request == null
                || request.getChargerId() == null
                || request.getChargerId() <= 0
                || request.getStartTime() == null) {

            return error(HttpStatus.BAD_REQUEST,
                    "Charger ID and booking start time are required.");
        }

        if (request.getDurationHours() < 1
                || request.getDurationHours() > 24) {

            return error(HttpStatus.BAD_REQUEST,
                    "Booking duration must be between 1 and 24 hours.");
        }

        LocalDateTime startTime = request.getStartTime();

        if (startTime.isBefore(LocalDateTime.now())) {
            return error(HttpStatus.BAD_REQUEST,
                    "Booking start time cannot be in the past.");
        }

        LocalDateTime endTime =
                startTime.plusHours(request.getDurationHours());

        CustomerAccount customer =
                customerRepository.findByEmailIgnoreCase(
                        authentication.getName()).orElse(null);

        if (customer == null) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Customer account was not found. Please log in again.");
        }

        Charger charger = chargerRepository.findById(
                request.getChargerId()).orElse(null);

        if (charger == null) {
            return error(HttpStatus.NOT_FOUND,
                    "The selected charger was not found.");
        }

        if (!charger.isAvailable()) {
            return error(HttpStatus.BAD_REQUEST,
                    "This charger is currently unavailable.");
        }

        boolean overlapping =
                bookingRepository.hasOverlappingBooking(
                        charger.getId(), startTime, endTime);

        if (overlapping) {
            return error(HttpStatus.CONFLICT,
                    "This charger is already booked during the selected time. Please choose another time.");
        }

        double totalPrice =
                charger.getPricePerHour() * request.getDurationHours();

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setCharger(charger);
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setTotalPrice(totalPrice);
        booking.setStatus("PENDING");
        booking.setPaymentStatus("PENDING");

        Booking savedBooking = bookingRepository.save(booking);

        // Notify the host about the new booking request.
        if (charger.getHost() != null) {
            notificationService.notifyUser(
                    charger.getHost().getEmail(),
                    "HOST",
                    "New booking request",
                    customer.getFullName() + " requested "
                            + charger.getName() + " from "
                            + startTime + " to " + endTime + ".",
                    savedBooking.getId());
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(savedBooking));
    }

    // NEW: Get occupied intervals for the live day/week calendar.
    // Dates use the local format YYYY-MM-DDTHH:mm:ss.
    @GetMapping("/charger/{chargerId}/availability")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getChargerAvailability(
            @PathVariable Long chargerId,
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end) {

        if (chargerId == null || chargerId <= 0) {
            return error(HttpStatus.BAD_REQUEST,
                    "A valid charger ID is required.");
        }

        if (start == null || end == null || !start.isBefore(end)) {
            return error(HttpStatus.BAD_REQUEST,
                    "The start time must be before the end time.");
        }

        if (!chargerRepository.existsById(chargerId)) {
            return error(HttpStatus.NOT_FOUND,
                    "The selected charger was not found.");
        }

        List<Map<String, Object>> intervals =
                bookingRepository.findActiveBookingsInRange(
                        chargerId, start, end)
                .stream()
                .map(booking -> {
                    Map<String, Object> interval =
                            new LinkedHashMap<>();

                    interval.put("startTime", booking.getStartTime());
                    interval.put("endTime", booking.getEndTime());
                    interval.put("status", booking.getStatus());

                    return interval;
                })
                .toList();

        return ResponseEntity.ok(intervals);
    }

    // Get booking history for the logged-in customer.
    @GetMapping("/customer")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getCustomerBookings(
            Authentication authentication) {

        if (!hasRole(authentication, "ROLE_CUSTOMER")) {
            return error(HttpStatus.FORBIDDEN,
                    "Only customers can access customer booking history.");
        }

        List<BookingResponse> bookings =
                bookingRepository.findBookingsForCustomer(
                        authentication.getName())
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(bookings);
    }

    // Get booking requests for the logged-in host.
    @GetMapping("/host")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getHostBookings(
            Authentication authentication) {

        if (!hasRole(authentication, "ROLE_HOST")) {
            return error(HttpStatus.FORBIDDEN,
                    "Only hosts can access host booking history.");
        }

        List<BookingResponse> bookings =
                bookingRepository.findBookingsForHost(
                        authentication.getName())
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(bookings);
    }

    // Approve a booking belonging to this host.
    @PatchMapping("/{bookingId}/approve")
    @Transactional
    public ResponseEntity<?> approveBooking(
            @PathVariable Long bookingId,
            Authentication authentication) {

        return updateBookingStatus(
                bookingId, "APPROVED", authentication);
    }

    // Reject a booking belonging to this host.
    @PatchMapping("/{bookingId}/reject")
    @Transactional
    public ResponseEntity<?> rejectBooking(
            @PathVariable Long bookingId,
            Authentication authentication) {

        return updateBookingStatus(
                bookingId, "REJECTED", authentication);
    }

    private ResponseEntity<?> updateBookingStatus(
            Long bookingId,
            String newStatus,
            Authentication authentication) {

        if (!hasRole(authentication, "ROLE_HOST")) {
            return error(HttpStatus.FORBIDDEN,
                    "Only the host who owns the charger can update a booking.");
        }

        if (bookingId == null || bookingId <= 0) {
            return error(HttpStatus.BAD_REQUEST,
                    "A valid booking ID is required.");
        }

        Booking booking = bookingRepository
                .findBookingWithDetails(bookingId)
                .orElse(null);

        if (booking == null) {
            return error(HttpStatus.NOT_FOUND,
                    "Booking not found.");
        }

        Charger charger = booking.getCharger();

        if (charger.getHost() == null
                || !charger.getHost().getEmail()
                    .equalsIgnoreCase(authentication.getName())) {

            return error(HttpStatus.FORBIDDEN,
                    "You cannot update a booking for another host's charger.");
        }

        if (!"PENDING".equalsIgnoreCase(booking.getStatus())) {
            return error(HttpStatus.CONFLICT,
                    "Only pending bookings can be approved or rejected.");
        }

        // Before approval, ensure no other active booking overlaps.
        if ("APPROVED".equals(newStatus)) {
            boolean overlapping =
                    bookingRepository.hasOverlappingBookingExcludingId(
                            charger.getId(),
                            booking.getId(),
                            booking.getStartTime(),
                            booking.getEndTime());

            if (overlapping) {
                return error(HttpStatus.CONFLICT,
                        "Another active booking overlaps this time. This booking cannot be approved.");
            }
        }

        booking.setStatus(newStatus);

        Booking savedBooking = bookingRepository.save(booking);

        // Notify the customer about host approval or rejection.
        CustomerAccount customer = booking.getCustomer();
        if (customer != null) {
            String title = "APPROVED".equals(newStatus)
                    ? "Booking approved" : "Booking rejected";
            String message = "Your booking for " + charger.getName()
                    + " (" + booking.getStartTime() + " to "
                    + booking.getEndTime() + ") was "
                    + newStatus.toLowerCase() + ".";
            notificationService.notifyUser(
                    customer.getEmail(), "CUSTOMER", title, message,
                    savedBooking.getId());
        }

        return ResponseEntity.ok(toResponse(savedBooking));
    }

    // Customer cancels their own future booking.
    @PatchMapping("/{bookingId}/cancel")
    @Transactional
    public ResponseEntity<?> cancelBooking(
            @PathVariable Long bookingId,
            Authentication authentication) {

        if (!hasRole(authentication, "ROLE_CUSTOMER")) {
            return error(HttpStatus.FORBIDDEN,
                    "Only the customer who made the booking can cancel it.");
        }

        if (bookingId == null || bookingId <= 0) {
            return error(HttpStatus.BAD_REQUEST,
                    "A valid booking ID is required.");
        }

        Booking booking = bookingRepository
                .findBookingWithDetails(bookingId)
                .orElse(null);

        if (booking == null) {
            return error(HttpStatus.NOT_FOUND, "Booking not found.");
        }

        CustomerAccount customer = booking.getCustomer();

        if (customer == null || customer.getEmail() == null
                || !customer.getEmail().equalsIgnoreCase(
                        authentication.getName())) {

            return error(HttpStatus.FORBIDDEN,
                    "You can only cancel your own bookings.");
        }

        String status = booking.getStatus() == null
                ? ""
                : booking.getStatus().toUpperCase();

        if (!"PENDING".equals(status)
                && !"APPROVED".equals(status)) {

            return error(HttpStatus.CONFLICT,
                    "Only pending or approved bookings can be cancelled.");
        }

        if (booking.getStartTime() == null
                || !booking.getStartTime().isAfter(LocalDateTime.now())) {

            return error(HttpStatus.CONFLICT,
                    "A booking cannot be cancelled after its start time.");
        }

        if ("VERIFIED".equalsIgnoreCase(
                booking.getPaymentStatus())) {

            return error(HttpStatus.CONFLICT,
                    "This payment is already verified. Contact the host to arrange any refund before cancellation.");
        }

        booking.setStatus("CANCELLED");

        Booking savedBooking = bookingRepository.save(booking);

        // Notify the charger host when the customer cancels a booking.
        Charger charger = booking.getCharger();
        if (charger != null && charger.getHost() != null
                && charger.getHost().getEmail() != null
                && !charger.getHost().getEmail().isBlank()) {

            String customerName = customer.getFullName() == null
                    || customer.getFullName().isBlank()
                    ? customer.getEmail()
                    : customer.getFullName();

            String message = customerName + " cancelled the booking for "
                    + charger.getName() + " (" + booking.getStartTime()
                    + " to " + booking.getEndTime() + ").";

            notificationService.notifyUser(
                    charger.getHost().getEmail(),
                    "HOST",
                    "Booking cancelled by customer",
                    message,
                    savedBooking.getId());
        }

        return ResponseEntity.ok(toResponse(savedBooking));
    }

    // Host manually confirms that customer payment was received.
    @PatchMapping("/{bookingId}/payment/verify")
    @Transactional
    public ResponseEntity<?> verifyPayment(
            @PathVariable Long bookingId,
            Authentication authentication) {

        return updatePaymentStatus(
                bookingId, "VERIFIED", authentication);
    }

    // Host marks a payment as rejected if it cannot be confirmed.
    @PatchMapping("/{bookingId}/payment/reject")
    @Transactional
    public ResponseEntity<?> rejectPayment(
            @PathVariable Long bookingId,
            Authentication authentication) {

        return updatePaymentStatus(
                bookingId, "REJECTED", authentication);
    }

    private ResponseEntity<?> updatePaymentStatus(
            Long bookingId,
            String newPaymentStatus,
            Authentication authentication) {

        if (!hasRole(authentication, "ROLE_HOST")) {
            return error(HttpStatus.FORBIDDEN,
                    "Only the host who owns the charger can verify a payment.");
        }

        if (bookingId == null || bookingId <= 0) {
            return error(HttpStatus.BAD_REQUEST,
                    "A valid booking ID is required.");
        }

        Booking booking = bookingRepository
                .findBookingWithDetails(bookingId)
                .orElse(null);

        if (booking == null) {
            return error(HttpStatus.NOT_FOUND, "Booking not found.");
        }

        Charger charger = booking.getCharger();

        if (charger.getHost() == null
                || !charger.getHost().getEmail()
                    .equalsIgnoreCase(authentication.getName())) {

            return error(HttpStatus.FORBIDDEN,
                    "You cannot update payment for another host's charger.");
        }

        if (!"APPROVED".equalsIgnoreCase(booking.getStatus())) {
            return error(HttpStatus.CONFLICT,
                    "Payment can only be verified for an approved booking.");
        }

        if (!"PENDING".equalsIgnoreCase(
                booking.getPaymentStatus())) {

            return error(HttpStatus.CONFLICT,
                    "Only pending payments can be verified or rejected.");
        }

        booking.setPaymentStatus(newPaymentStatus);

        Booking savedBooking = bookingRepository.save(booking);

        // Notify the customer about payment verification or rejection.
        CustomerAccount customer = booking.getCustomer();
        if (customer != null) {
            String title = "VERIFIED".equals(newPaymentStatus)
                    ? "Payment verified" : "Payment rejected";
            String message = "Your payment for " + charger.getName()
                    + " (" + booking.getStartTime() + ") was marked "
                    + newPaymentStatus.toLowerCase() + ".";
            notificationService.notifyUser(
                    customer.getEmail(), "CUSTOMER", title, message,
                    savedBooking.getId());
        }

        return ResponseEntity.ok(toResponse(savedBooking));
    }

    // Convert a booking entity into its API response.
    private BookingResponse toResponse(Booking booking) {

        BookingResponse response = new BookingResponse();

        response.setId(booking.getId());

        Charger charger = booking.getCharger();
        CustomerAccount customer = booking.getCustomer();

        response.setChargerId(charger.getId());
        response.setChargerName(charger.getName());
        response.setCity(charger.getCity());
        response.setAddress(charger.getAddress());
        response.setVehicleType(charger.getVehicleType());

        response.setCustomerName(customer.getFullName());
        response.setCustomerEmail(customer.getEmail());

        if (charger.getHost() != null) {
            response.setHostEmail(charger.getHost().getEmail());
        }

        response.setStartTime(booking.getStartTime());
        response.setEndTime(booking.getEndTime());
        response.setTotalPrice(booking.getTotalPrice());
        response.setStatus(booking.getStatus());

        response.setPaymentStatus(booking.getPaymentStatus());
        response.setPaymentQrPath(charger.getPaymentQrPath());
        response.setCreatedAt(booking.getCreatedAt());

        return response;
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
            HttpStatus status,
            String message) {

        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message);

        return ResponseEntity.status(status).body(body);
    }
}
