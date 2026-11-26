package com.digitalwill.auth.web;

import com.digitalwill.auth.security.UserPrincipal;
import com.digitalwill.auth.security.SessionCredentials;
import com.digitalwill.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = Objects.requireNonNull(authService);
    }

    public record RegisterRequest(
            @NotBlank(message = "Email must not be blank")
            @Email(message = "Email must be valid")
            @Size(max = 255)
            String email,

            @NotBlank(message = "Password must not be blank")
            @Size(min = 8, message = "Password must be at least 8 characters")
            String password,

            @NotBlank(message = "Full name must not be blank")
            @Size(max = 255)
            String fullName
    ) {}

    public record LoginRequest(
            @NotBlank(message = "Email must not be blank")
            String email,

            @NotBlank(message = "Password must not be blank")
            String password
    ) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        AuthService.AuthResponse response = authService.register(request.email(), request.password(), request.fullName());
        return sessionResponse(HttpStatus.CREATED, response, httpRequest);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthService.AuthResponse response = authService.login(request.email(), request.password());
        return sessionResponse(HttpStatus.OK, response, httpRequest);
    }

    private record BrowserProfile(UUID userId, String email, String fullName, UUID willId) {}

    private ResponseEntity<?> sessionResponse(HttpStatus status, AuthService.AuthResponse session,
                                             HttpServletRequest request) {
        var response = ResponseEntity.status(status);
        if (!SessionCredentials.browserRequest(request)) return response.body(session);
        return response.header(HttpHeaders.SET_COOKIE, SessionCredentials.cookie(session.token(), SessionCredentials.LIFETIME).toString())
                .body(new BrowserProfile(session.userId(), session.email(), session.fullName(), session.willId()));
    }

    @GetMapping("/session")
    public ResponseEntity<AuthService.UserProfile> session(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(authService.getProfile(principal.getId()));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthService.UserProfile> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new SecurityException("Authentication required");
        }
        AuthService.UserProfile profile = authService.getProfile(principal.getId());
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        authService.logout(SessionCredentials.from(request));
        var response = ResponseEntity.ok();
        if (SessionCredentials.browserRequest(request)) {
            response.header(HttpHeaders.SET_COOKIE, SessionCredentials.cookie("", Duration.ZERO).toString());
        }
        return response.body(Map.of("message", "Logged out successfully"));
    }
}
