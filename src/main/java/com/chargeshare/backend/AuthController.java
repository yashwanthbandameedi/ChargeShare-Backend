
package com.chargeshare.backend;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {
    "http://localhost:5173",
    "http://localhost:5174"
})
public class AuthController {

    private final HostAccountRepository hostAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(
            HostAccountRepository hostAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.hostAccountRepository = hostAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record SignupRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password
    ) {}

    public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String password
    ) {}

    public record AuthResponse(
        String message,
        Long hostId,
        String fullName,
        String email,
        String token
    ) {}

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(
            @Valid @RequestBody SignupRequest request) {

        String email = request.email().trim().toLowerCase();

        if (hostAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "An account with this email already exists"
            );
        }

        HostAccount account = new HostAccount(
            request.fullName().trim(),
            email,
            passwordEncoder.encode(request.password())
        );

        HostAccount saved = hostAccountRepository.save(account);
        String token = jwtService.generateToken(saved);

        return new AuthResponse(
            "Host account created successfully",
            saved.getId(),
            saved.getFullName(),
            saved.getEmail(),
            token
        );
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request) {

        String email = request.email().trim().toLowerCase();

        HostAccount account = hostAccountRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password"
            ));

        if (!passwordEncoder.matches(
                request.password(), account.getPassword())) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password"
            );
        }

        String token = jwtService.generateToken(account);

        return new AuthResponse(
            "Login successful",
            account.getId(),
            account.getFullName(),
            account.getEmail(),
            token
        );
    }
}
