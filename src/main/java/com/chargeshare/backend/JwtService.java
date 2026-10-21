
package com.chargeshare.backend;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;

    private final long expirationMillis = 1000L * 60 * 60 * 8;

    public JwtService(
            @Value("${chargeshare.jwt.secret}") String secret) {

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                "CHARGESHARE_JWT_SECRET must be at least 32 bytes long"
            );
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // Existing host-token method: preserve compatibility.
    public String generateToken(HostAccount account) {
        return generateToken(account.getEmail(), "HOST", account.getId());
    }

    // Generate tokens for hosts and customers.
    public String generateToken(
            String email,
            String role,
            Long accountId) {

        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMillis);

        var builder = Jwts.builder()
            .subject(email)
            .claim("role", role)
            .issuedAt(now)
            .expiration(expiration);

        if (accountId != null) {
            if ("HOST".equals(role)) {
                builder.claim("hostId", accountId);
            } else if ("CUSTOMER".equals(role)) {
                builder.claim("customerId", accountId);
            }
        }

        return builder
            .signWith(secretKey)
            .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);

            return claims.getSubject() != null
                && claims.get("role", String.class) != null;

        } catch (io.jsonwebtoken.JwtException |
                 IllegalArgumentException exception) {
            return false;
        }
    }
}
