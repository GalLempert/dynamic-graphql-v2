package iaf.ofek.sigma.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.Nullable;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures every request has an {@code X-Request-ID} that is propagated via MDC and the response header.
 * <p>
 * When possible, the filter reuses the current tracing span's trace identifier so logs correlate with
 * metrics and traces out-of-the-box. If no span is active, a fresh identifier is generated.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-ID";

    private final Tracer tracer;

    public RequestIdFilter(@Nullable Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String headerRequestId = request.getHeader(HEADER_NAME);
        boolean requestIdCameFromHeader = StringUtils.hasText(headerRequestId);

        String requestId = requestIdCameFromHeader ? headerRequestId : resolveCurrentTraceId();
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put("requestId", requestId);
        response.setHeader(HEADER_NAME, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }

        if (!requestIdCameFromHeader && !StringUtils.hasText(headerRequestId)) {
            String traceId = resolveCurrentTraceId();
            if (StringUtils.hasText(traceId)) {
                response.setHeader(HEADER_NAME, traceId);
            }
        }
    }

    @Nullable
    private String resolveCurrentTraceId() {
        if (this.tracer == null) {
            return null;
        }
        Span currentSpan = this.tracer.currentSpan();
        if (currentSpan == null || currentSpan.context() == null) {
            return null;
        }
        return currentSpan.context().traceId();
    }
}
