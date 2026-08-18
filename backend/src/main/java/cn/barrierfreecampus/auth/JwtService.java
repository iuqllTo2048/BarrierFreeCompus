package cn.barrierfreecampus.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Clock clock;

    @Autowired
    public JwtService(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.access-token-ttl:PT15M}") Duration accessTokenTtl) {
        this(secret, accessTokenTtl, Clock.systemUTC());
    }

    JwtService(String secret, Duration accessTokenTtl, Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = accessTokenTtl;
        this.clock = clock;
    }

    public String issue(String username, String role) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    public JwtPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new JwtPrincipal(claims.getSubject(), claims.get("role", String.class));
    }

    public record JwtPrincipal(String username, String role) {
    }
}
