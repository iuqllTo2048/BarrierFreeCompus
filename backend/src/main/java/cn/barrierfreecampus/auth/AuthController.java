package cn.barrierfreecampus.auth;

import cn.barrierfreecampus.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final Duration refreshTokenTtl;
    private final boolean secureCookie;

    public AuthController(
            AuthService authService,
            @Value("${app.security.refresh-token-ttl:P7D}") Duration refreshTokenTtl,
            @Value("${app.security.secure-cookie:false}") boolean secureCookie) {
        this.authService = authService;
        this.refreshTokenTtl = refreshTokenTtl;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/login")
    public ApiResponse<AuthService.LoginResult> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthService.TokenResult result = authService.login(request.username(), request.password());
        setRefreshCookie(response, result.refreshToken());
        return ApiResponse.ok(result.user());
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthService.LoginResult> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String token,
            HttpServletResponse response) {
        AuthService.TokenResult result = authService.refresh(token);
        setRefreshCookie(response, result.refreshToken());
        return ApiResponse.ok(result.user());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String token,
            HttpServletResponse response) {
        authService.logout(token);
        clearRefreshCookie(response);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUser> me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .orElse("");
        return ApiResponse.ok(new CurrentUser(authentication.getName(), role));
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(refreshTokenTtl)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record CurrentUser(String username, String role) {
    }
}
