package cn.barrierfreecampus.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHashService tokenHashService;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TokenHashService tokenHashService,
            @Value("${app.security.refresh-token-ttl:P7D}") Duration refreshTokenTtl) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenHashService = tokenHashService;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public TokenResult login(String username, String password) {
        User user = findEnabledUser(username).stream()
                .findFirst()
                .filter(candidate -> passwordEncoder.matches(password, candidate.passwordHash()))
                .orElseThrow(() -> unauthorized("用户名或密码错误"));
        audit(user.id(), "LOGIN", user.username());
        return issue(user);
    }

    @Transactional
    public TokenResult refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw unauthorized("登录已过期，请重新登录");
        }
        String hash = tokenHashService.hash(rawToken);
        StoredToken stored = findActiveToken(hash).stream()
                .findFirst()
                .orElseThrow(() -> unauthorized("登录已过期，请重新登录"));
        int revoked = jdbc.update(
                "UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP WHERE id = ? AND revoked_at IS NULL",
                stored.tokenId());
        if (revoked != 1) {
            throw unauthorized("登录已过期，请重新登录");
        }
        audit(stored.user().id(), "TOKEN_REFRESH", stored.user().username());
        return issue(stored.user());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String hash = tokenHashService.hash(rawToken);
        List<StoredToken> matches = findActiveToken(hash);
        if (matches.isEmpty()) {
            return;
        }
        StoredToken stored = matches.getFirst();
        jdbc.update("UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP WHERE id = ?", stored.tokenId());
        audit(stored.user().id(), "LOGOUT", stored.user().username());
    }

    private List<User> findEnabledUser(String username) {
        return jdbc.query(
                "SELECT id, username, password_hash, role FROM app_user WHERE username = ? AND enabled = TRUE",
                (resultSet, rowNumber) -> new User(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("role")),
                username);
    }

    private List<StoredToken> findActiveToken(String hash) {
        return jdbc.query(
                """
                SELECT t.id AS token_id, u.id AS user_id, u.username, u.password_hash, u.role
                FROM refresh_token t
                JOIN app_user u ON u.id = t.user_id
                WHERE t.token_hash = ?
                  AND t.revoked_at IS NULL
                  AND t.expires_at > CURRENT_TIMESTAMP
                  AND u.enabled = TRUE
                """,
                (resultSet, rowNumber) -> new StoredToken(
                        resultSet.getObject("token_id", UUID.class),
                        new User(
                                resultSet.getLong("user_id"),
                                resultSet.getString("username"),
                                resultSet.getString("password_hash"),
                                resultSet.getString("role"))),
                hash);
    }

    private TokenResult issue(User user) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        jdbc.update(
                "INSERT INTO refresh_token(id, user_id, token_hash, expires_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                user.id(),
                tokenHashService.hash(refreshToken),
                OffsetDateTime.now().plus(refreshTokenTtl));
        return new TokenResult(
                new LoginResult(user.username(), user.role(), jwtService.issue(user.username(), user.role())),
                refreshToken);
    }

    private void audit(long actorId, String action, String username) {
        jdbc.update(
                "INSERT INTO audit_log(actor_id, action, target_type, target_id) VALUES (?, ?, 'USER', ?)",
                actorId,
                action,
                username);
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private record User(long id, String username, String passwordHash, String role) {
    }

    private record StoredToken(UUID tokenId, User user) {
    }

    public record LoginResult(String username, String role, String accessToken) {
    }

    public record TokenResult(LoginResult user, String refreshToken) {
    }
}
