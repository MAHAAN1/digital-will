package com.digitalwill.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

public final class SessionCredentials {
    public static final String COOKIE_NAME = "__Host-digitalwill";
    public static final Duration LIFETIME = Duration.ofDays(30);

    private SessionCredentials() {}

    public static boolean browserRequest(HttpServletRequest request) {
        // Only trusted origins may send this non-simple header through the CORS filter.
        return "DigitalWill".equals(request.getHeader("X-Requested-With"));
    }

    public static String from(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null) {
            return authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : null;
        }
        if (browserRequest(request) && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
            }
        }
        return null;
    }

    public static ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(true)
                .sameSite("Strict").path("/").maxAge(maxAge).build();
    }
}
