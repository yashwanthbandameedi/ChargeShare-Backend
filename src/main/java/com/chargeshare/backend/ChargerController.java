
package com.chargeshare.backend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chargers")
@CrossOrigin(origins = {
    "http://localhost:5173",
    "http://localhost:5174",
    "http://localhost:5175",
    "http://localhost:5176",
    "https://charge-share-frontend.vercel.app"
})
public class ChargerController {

    private static final long MAX_QR_SIZE = 5 * 1024 * 1024;

    private static final Path QR_UPLOAD_DIR =
            Paths.get("uploads", "payment-qr")
                    .toAbsolutePath()
                    .normalize();

    private final ChargerRepository chargerRepository;

    public ChargerController(ChargerRepository chargerRepository) {
        this.chargerRepository = chargerRepository;
    }

    private boolean isLoggedInHost(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                    .anyMatch(authority ->
                        "ROLE_HOST".equals(authority.getAuthority()));
    }

    // Search available chargers publicly.
    @GetMapping
    public List<Charger> searchChargers(
            @RequestParam String city,
            @RequestParam String vehicleType) {

        return chargerRepository
                .findByCityContainingIgnoreCaseAndVehicleTypeIgnoreCaseAndAvailableTrue(
                        city, vehicleType);
    }

    // Return only chargers owned by the logged-in host.
    @GetMapping("/mine")
    public ResponseEntity<?> getMyChargers(
            Authentication authentication) {

        if (!isLoggedInHost(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                        "error",
                        "Only logged-in hosts can access their chargers."
                    ));
        }

        String hostEmail = authentication.getName();

        return ResponseEntity.ok(
                chargerRepository.findByHost_EmailIgnoreCase(hostEmail));
    }

    // Upload or replace the payment QR image for a host-owned charger.
    @PostMapping(
        value = "/{chargerId}/payment-qr",
        consumes = "multipart/form-data"
    )
    @Transactional
    public ResponseEntity<?> uploadPaymentQr(
            @PathVariable Long chargerId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        if (!isLoggedInHost(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                        "error",
                        "Only logged-in hosts can upload payment QR codes."
                    ));
        }

        if (chargerId == null || chargerId <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                        "error",
                        "A valid charger ID is required."
                    ));
        }

        Charger charger = chargerRepository.findById(chargerId)
                .orElse(null);

        if (charger == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Charger not found."));
        }

        if (charger.getHost() == null
                || !charger.getHost().getEmail()
                    .equalsIgnoreCase(authentication.getName())) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                        "error",
                        "You can only upload a QR code for your own charger."
                    ));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                        "error",
                        "Please select a QR image."
                    ));
        }

        if (file.getSize() > MAX_QR_SIZE) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                        "error",
                        "QR image must be 5 MB or smaller."
                    ));
        }

        String contentType = file.getContentType();

        Map<String, String> allowedTypes = Map.of(
                "image/png", ".png",
                "image/jpeg", ".jpg"
        );

        if (contentType == null
                || !allowedTypes.containsKey(contentType)) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                        "error",
                        "Only PNG and JPEG QR images are allowed."
                    ));
        }

        String filename = UUID.randomUUID()
                + allowedTypes.get(contentType);

        Path destination = QR_UPLOAD_DIR.resolve(filename).normalize();

        if (!destination.startsWith(QR_UPLOAD_DIR)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid file path."));
        }

        try {
            Files.createDirectories(QR_UPLOAD_DIR);

            try (var inputStream = file.getInputStream()) {
                Files.copy(
                        inputStream,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            String qrPath = "/payment-qr/" + filename;
            charger.setPaymentQrPath(qrPath);
            chargerRepository.save(charger);

            return ResponseEntity.ok(Map.of(
                    "message", "Payment QR uploaded successfully.",
                    "chargerId", charger.getId().toString(),
                    "paymentQrPath", qrPath
            ));

        } catch (IOException exception) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error",
                        "Unable to save the QR image."
                    ));
        }
    }
}
