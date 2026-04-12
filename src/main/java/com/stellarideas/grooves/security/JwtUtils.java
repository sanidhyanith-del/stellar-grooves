package com.stellarideas.grooves.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);
    private static final String DEFAULT_SECRET = "c3RlbGxhci1ncm9vdmVzLXNlY3JldC1rZXktZm9yLWp3dC1wbGVhc2UtY2hhbmdlLWluLXByb2R1Y3Rpb24tZW52";

    @Value("${stellar.grooves.jwtSecret}")
    private String jwtSecret;

    @Value("${stellar.grooves.jwtExpirationMs:86400000}")
    private int jwtExpirationMs;

    @Value("${stellar.grooves.requireSecureJwt:false}")
    private boolean requireSecureJwt;

    @PostConstruct
    void validateJwtSecret() {
        if (DEFAULT_SECRET.equals(jwtSecret)) {
            if (requireSecureJwt) {
                throw new IllegalStateException(
                        "JWT secret must be changed from the default value. "
                        + "Set the JWT_SECRET environment variable or stellar.grooves.jwtSecret property.");
            }
            logger.warn("*** Using default JWT secret — this is NOT safe for production. "
                    + "Set JWT_SECRET env var or stellar.grooves.requireSecureJwt=true to enforce. ***");
        }
    }

    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public String generateJwtToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        return Jwts.builder()
                .setSubject(userPrincipal.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key())
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken);
            return true;
        } catch (MalformedJwtException e) {
            logger.warn("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }
}
