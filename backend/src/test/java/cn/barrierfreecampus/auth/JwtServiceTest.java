package cn.barrierfreecampus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "test-secret-that-is-longer-than-thirty-two-bytes";
    private final JwtService jwtService = new JwtService(SECRET, Duration.ofMinutes(15), Clock.systemUTC());

    @Test
    void shouldIssueAndParseSignedToken() {
        String token = jwtService.issue("demo_admin", "ADMIN");

        JwtService.JwtPrincipal principal = jwtService.parse(token);

        assertThat(principal.username()).isEqualTo("demo_admin");
        assertThat(principal.role()).isEqualTo("ADMIN");
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = jwtService.issue("demo_user", "USER");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(JwtException.class);
    }
}
