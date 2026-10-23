
package com.chargeshare.backend;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chargers")
@CrossOrigin(origins = {
    "http://localhost:5173",
    "http://localhost:5174",
    "http://localhost:5175",
    "http://localhost:5176"
})
public class HostController {

    private final ChargerRepository chargerRepository;
    private final HostAccountRepository hostAccountRepository;

    public HostController(
            ChargerRepository chargerRepository,
            HostAccountRepository hostAccountRepository) {
        this.chargerRepository = chargerRepository;
        this.hostAccountRepository = hostAccountRepository;
    }

    public record ChargerRequest(
        @NotBlank String name,
        @NotBlank String city,
        @NotBlank String address,
        @NotBlank String vehicleType,
        @NotNull @Min(1) Double pricePerHour,
        Double latitude,
        Double longitude
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Charger registerCharger(
            @Valid @RequestBody ChargerRequest request,
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Please log in as a host first"
            );
        }

        HostAccount host = hostAccountRepository
            .findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Host account not found. Please log in again."
            ));

        String vehicleType = request.vehicleType()
            .trim()
            .toLowerCase(java.util.Locale.ROOT);

        if (!vehicleType.equals("car")
                && !vehicleType.equals("bike")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Vehicle type must be car or bike"
            );
        }

        Double latitude = request.latitude();
        Double longitude = request.longitude();

        // Both coordinates must be provided together.
        if ((latitude == null) != (longitude == null)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Provide both latitude and longitude, or leave both empty."
            );
        }

        // Validate coordinate ranges and reject NaN or infinity.
        if (latitude != null
                && (!Double.isFinite(latitude)
                    || !Double.isFinite(longitude)
                    || latitude < -90.0
                    || latitude > 90.0
                    || longitude < -180.0
                    || longitude > 180.0)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "The supplied coordinates are invalid."
            );
        }

        Charger charger = new Charger(
            request.name().trim(),
            request.city().trim(),
            request.address().trim(),
            vehicleType,
            request.pricePerHour(),
            true
        );

        charger.setLatitude(latitude);
        charger.setLongitude(longitude);
        charger.setHost(host);

        return chargerRepository.save(charger);
    }
}
