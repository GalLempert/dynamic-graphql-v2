package iaf.ofek.sigma.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ensures every incoming request is associated with a request identifier that also
 * appears in logs and response headers. When tracing is active, the current trace ID
 * is reused to allow correlation between metrics, traces and logs. Otherwise a
 * UUIDv7 identifier is generated.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-Request-ID";

    private final Tracer tracer;

    public RequestIdFilter(@Nullable Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        try {
            String incomingRequestId = request.getHeader(HEADER_NAME);
            String traceId = getCurrentTraceId();
            String spanId = getCurrentSpanId();

            String requestId = incomingRequestId != null ? incomingRequestId :
                    (traceId != null ? traceId : generateUuidV7());

            putIfNotBlank("requestId", requestId);
            putIfNotBlank("traceId", traceId);
            putIfNotBlank("spanId", spanId);

            response.setHeader(HEADER_NAME, requestId);

            filterChain.doFilter(request, response);
        } finally {
            if (previousContext != null) {
                MDC.setContextMap(previousContext);
            } else {
                MDC.clear();
            }
        }
    }

    private void putIfNotBlank(String key, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    @Nullable
    private String getCurrentTraceId() {
        Span span = getCurrentSpan();
        return span != null ? span.context().traceId() : null;
    }

    @Nullable
    private String getCurrentSpanId() {
        Span span = getCurrentSpan();
        return span != null ? span.context().spanId() : null;
    }

    @Nullable
    private Span getCurrentSpan() {
        return tracer != null ? tracer.currentSpan() : null;
    }

    /**
     * Generates a monotonic UUIDv7 identifier as defined in RFC 9562.
     */
    String generateUuidV7() {
        long unixMilliseconds = Instant.now().toEpochMilli() & 0xFFFFFFFFFFFFL;
        long randomA = ThreadLocalRandom.current().nextLong(1L << 12);
        long randomB = ThreadLocalRandom.current().nextLong(1L << 62);

        long mostSignificantBits = (unixMilliseconds << 16)
                | 0x7000
                | randomA;

        long leastSignificantBits = randomB | 0x8000000000000000L;

        return new UUID(mostSignificantBits, leastSignificantBits).toString();
    }
}
