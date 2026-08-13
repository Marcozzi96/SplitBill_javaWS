package it.javaWS.filters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate limiting in-memory (finestra fissa per IP + endpoint) sugli endpoint di autenticazione.
 * Adatto a un deploy su singola istanza: il conteggio non è condiviso tra più istanze.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/auth/login",
            "/auth/register",
            "/auth/forgotPassword",
            "/auth/resetPassword"
    );

    private final int limit;
    private final long windowMillis;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(@Value("${app.rate-limit.limit:10}") int limit,
                               @Value("${app.rate-limit.window-seconds:60}") long windowSeconds) {
        this.limit = limit;
        this.windowMillis = windowSeconds * 1000;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String key = resolveClientIp(request) + ":" + request.getRequestURI();
        long now = System.currentTimeMillis();

        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= windowMillis) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        // Pulizia opportunistica per evitare crescita illimitata della mappa
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().start >= windowMillis);
        }

        if (window.count.get() > limit) {
            log.warn("Rate limit superato per {} su {}", resolveClientIp(request), request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\","
                    + "\"status\":" + HttpStatus.TOO_MANY_REQUESTS.value() + ","
                    + "\"error\":\"Too Many Requests\","
                    + "\"message\":\"Troppe richieste, riprovare più tardi\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        private final long start;
        private final AtomicInteger count;

        private Window(long start, AtomicInteger count) {
            this.start = start;
            this.count = count;
        }
    }
}
