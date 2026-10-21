
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
    "http://localhost:5174"
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
        @NotNull @Min(1) Double pricePerHour
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Charger registerCharger(
            @Valid @RequestBody ChargerRequest request,
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Please log in as a host first"
            );
        }

        String email = authentication.getName();

        HostAccount host = hostAccountRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Host account not found. Please log in again."
            ));

        String vehicleType =
            request.vehicleType().trim().toLowerCase();

        if (!vehicleType.equals("car")
                && !vehicleType.equals("bike")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Vehicle type must be car or bike"
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

        charger.setHost(host);

        return chargerRepository.save(charger);
    }
}
