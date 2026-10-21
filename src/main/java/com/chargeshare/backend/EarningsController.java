package com.chargeshare.backend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/earnings")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174",
        "http://localhost:5175",
        "http://localhost:5176"
})
public class EarningsController {

    private final BookingRepository bookingRepository;

    public EarningsController(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    // Return earnings for the authenticated host only.
    @GetMapping("/host")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getHostEarnings(Authentication authentication) {
        if (!hasRole(authentication, "ROLE_HOST")) {
            return error(HttpStatus.FORBIDDEN,
                    "Only logged-in hosts can access the earnings dashboard.");
        }

        List<Booking> bookings = bookingRepository
                .findEarningsBookingsForHost(authentication.getName());

        LocalDateTime now = LocalDateTime.now();
        int currentYear = LocalDate.now().getYear();

        BigDecimal verifiedEarnings = BigDecimal.ZERO;
        BigDecimal pendingPayments = BigDecimal.ZERO;
        int completedBookings = 0;
        List<Map<String, Object>> monthlyEarnings = new ArrayList<>();
        BigDecimal[] monthlyTotals = new BigDecimal[12];
        for (int i = 0; i < monthlyTotals.length; i++) {
            monthlyTotals[i] = BigDecimal.ZERO;
        }

        for (Booking booking : bookings) {
            String bookingStatus = normalize(booking.getStatus());
            String paymentStatus = normalize(booking.getPaymentStatus());
            BigDecimal amount = BigDecimal.valueOf(booking.getTotalPrice());

            boolean activeBooking = "APPROVED".equals(bookingStatus);
            boolean verified = "VERIFIED".equals(paymentStatus);
            boolean pending = "PENDING".equals(paymentStatus);

            if (activeBooking && verified) {
                verifiedEarnings = verifiedEarnings.add(amount);

                if (booking.getStartTime() != null
                        && booking.getStartTime().getYear() == currentYear) {
                    int monthIndex = booking.getStartTime().getMonthValue() - 1;
                    monthlyTotals[monthIndex] = monthlyTotals[monthIndex].add(amount);
                }
            }

            if (activeBooking && pending) {
                pendingPayments = pendingPayments.add(amount);
            }

            if (activeBooking && booking.getEndTime() != null
                    && !booking.getEndTime().isAfter(now)) {
                completedBookings++;
            }
        }

        for (Month month : Month.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH));
            item.put("monthNumber", month.getValue());
            item.put("year", currentYear);
            item.put("amount", money(monthlyTotals[month.getValue() - 1]));
            monthlyEarnings.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("currency", "INR");
        response.put("year", currentYear);
        response.put("verifiedEarnings", money(verifiedEarnings));
        response.put("pendingPayments", money(pendingPayments));
        response.put("completedBookings", completedBookings);
        response.put("monthlyEarnings", monthlyEarnings);

        return ResponseEntity.ok(response);
    }

    private boolean hasRole(Authentication authentication, String requiredRole) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> requiredRole.equals(authority.getAuthority()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private ResponseEntity<Map<String, String>> error(
            HttpStatus status, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
