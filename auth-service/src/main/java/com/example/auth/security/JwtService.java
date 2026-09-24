package com.example.auth.security;

import com.example.auth.entity.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN  = "access";
    private static final String REFRESH_TOKEN = "refresh";
    private static final String TEMP_TOKEN    = "temp";
    private static final String ROLES_CLAIM   = "roles";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;

        byte[] keyBytes = Decoders.BASE64.decode(properties.secret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(
            AuthUser user,
            Collection<String> roles
    ) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenTtl());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("username", user.getUsername())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN)
                .claim(ROLES_CLAIM, roles)
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(AuthUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.refreshTokenTtl());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Short-lived (5 min) token issued after password verification when 2FA is enabled.
     * Client must exchange this with a valid TOTP/backup code to get real tokens.
     */
    public String generateTempToken(AuthUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(java.time.Duration.ofMinutes(5));
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(TOKEN_TYPE_CLAIM, TEMP_TOKEN)
                .signWith(signingKey)
                .compact();
    }

    public boolean isTempToken(String token) {
        try {
            Claims claims = parseToken(token);
            return TEMP_TOKEN.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Claims parseToken(String token) {        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(String token) {
        try {
            Claims claims = parseToken(token);
            return ACCESS_TOKEN.equals(
                    claims.get(TOKEN_TYPE_CLAIM, String.class)
            );
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            return REFRESH_TOKEN.equals(
                    claims.get(TOKEN_TYPE_CLAIM, String.class)
            );
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public UUID getUserId(String token) {
        Claims claims = parseToken(token);
        return UUID.fromString(claims.getSubject());
    }

    public String getJti(String token) {
        Claims claims = parseToken(token);
        return claims.getId();
    }

    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    @SuppressWarnings("unchecked")
    public Collection<String> getRoles(String token) {
        Claims claims = parseToken(token);

        Object value = claims.get(ROLES_CLAIM);

        if (value == null) {
            return java.util.List.of();
        }

        return ((Collection<?>) value)
                .stream()
                .map(Object::toString)
                .toList();
    }

    public Instant getExpiration(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration().toInstant();
    }
}