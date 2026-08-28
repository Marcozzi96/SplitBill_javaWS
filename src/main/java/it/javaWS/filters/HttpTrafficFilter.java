package it.javaWS.filters;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Conta il traffico HTTP dell'applicazione (richieste, byte in/out, classi di status,
 * tempo totale) per l'endpoint di metriche GET /api/status.
 * Le richieste a /api/status stesso sono escluse: il frontend fa polling su quell'endpoint
 * e contarle inquinerebbe le metriche (es. richieste/minuto).
 */
@Component
public class HttpTrafficFilter extends OncePerRequestFilter {

    private static final String PERCORSO_ESCLUSO = "/api/status";

    private final AtomicLong requestsTotal = new AtomicLong();
    private final AtomicLong bytesInTotal = new AtomicLong();
    private final AtomicLong bytesOutTotal = new AtomicLong();
    private final AtomicLong requests2xx = new AtomicLong();
    private final AtomicLong requests4xx = new AtomicLong();
    private final AtomicLong requests5xx = new AtomicLong();
    private final AtomicLong totalTimeMs = new AtomicLong();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PERCORSO_ESCLUSO.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long inizio = System.currentTimeMillis();
        long bytesIn = Math.max(0, request.getContentLengthLong());

        // Wrapper che memorizza il corpo della risposta per contarne i byte scritti
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(request, wrappedResponse);
        } finally {
            int status = wrappedResponse.getStatus();
            requestsTotal.incrementAndGet();
            bytesInTotal.addAndGet(bytesIn);
            bytesOutTotal.addAndGet(wrappedResponse.getContentSize());
            totalTimeMs.addAndGet(System.currentTimeMillis() - inizio);
            if (status >= 200 && status < 300) {
                requests2xx.incrementAndGet();
            } else if (status >= 400 && status < 500) {
                requests4xx.incrementAndGet();
            } else if (status >= 500) {
                requests5xx.incrementAndGet();
            }
            // Svuota il buffer del wrapper nella risposta reale
            wrappedResponse.copyBodyToResponse();
        }
    }

    /**
     * Snapshot dei contatori, letto dal service delle metriche.
     */
    public Snapshot snapshot() {
        return new Snapshot(
                requestsTotal.get(),
                bytesInTotal.get(),
                bytesOutTotal.get(),
                requests2xx.get(),
                requests4xx.get(),
                requests5xx.get(),
                totalTimeMs.get());
    }

    public record Snapshot(long requestsTotal, long bytesInTotal, long bytesOutTotal,
                           long requests2xx, long requests4xx, long requests5xx, long totalTimeMs) {
    }
}
