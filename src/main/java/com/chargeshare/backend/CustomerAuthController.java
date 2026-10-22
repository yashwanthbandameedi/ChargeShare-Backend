
package com.chargeshare.backend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/customer-auth")
@CrossOrigin(origins = {
    "http://localhost:5173",
    "http://localhost:5174",
    "http://localhost:5175",
    "http://localhost:5176",
    "https://charge-share-frontend.vercel.app"
})
public class CustomerAuthController {

    private final CustomerAccountRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public CustomerAuthController(
            CustomerAccountRepository customerRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record CustomerAuthResponse(
            String message,
            Long customerId,
            String fullName,
            String email,
            String token) {
    }

    public record SignupRequest(
            String fullName,
            String email,
            String password) {
    }

    public record LoginRequest(
            String email,
            String password) {
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(
            @RequestBody SignupRequest request) {

        if (request.fullName() == null
                || request.fullName().isBlank()
                || request.email() == null
                || request.email().isBlank()
                || request.password() == null
                || request.password().length() < 8) {

            return ResponseEntity.badRequest().body(
                Map.of(
                    "message",
                    "Name and email are required. Password must contain at least 8 characters."
                )
            );
        }

        String fullName = request.fullName().trim();
        String email = request.email().trim().toLowerCase();

        if (!Pattern.matches(
                "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                email)) {

            return ResponseEntity.badRequest().body(
                Map.of("message", "Please enter a valid email address.")
            );
        }

        if (customerRepository.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                Map.of("message", "An account with this email already exists.")
            );
        }

        CustomerAccount customer = new CustomerAccount(
            fullName,
            email,
            passwordEncoder.encode(request.password())
        );

        customer = customerRepository.save(customer);

        String token = jwtService.generateToken(
            customer.getEmail(),
            "CUSTOMER",
            customer.getId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
            new CustomerAuthResponse(
                "Customer account created successfully.",
                customer.getId(),
                customer.getFullName(),
                customer.getEmail(),
                token
            )
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request) {

        if (request.email() == null
                || request.email().isBlank()
                || request.password() == null
                || request.password().isBlank()) {

            return ResponseEntity.badRequest().body(
                Map.of("message", "Email and password are required.")
            );
        }

        String email = request.email().trim().toLowerCase();

        var customerOptional =
            customerRepository.findByEmailIgnoreCase(email);

        if (customerOptional.isEmpty()
                || !passwordEncoder.matches(
                    request.password(),
                    customerOptional.get().getPassword())) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of("message", "Invalid email or password.")
            );
        }

        CustomerAccount customer = customerOptional.get();

        String token = jwtService.generateToken(
            customer.getEmail(),
            "CUSTOMER",
            customer.getId()
        );

        return ResponseEntity.ok(
            new CustomerAuthResponse(
                "Customer login successful.",
                customer.getId(),
                customer.getFullName(),
                customer.getEmail(),
                token
            )
        );
    }
}
