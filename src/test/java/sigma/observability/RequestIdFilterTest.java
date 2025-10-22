package sigma.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.ServletException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestIdFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldPropagateIncomingRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, "incoming-request");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RequestIdFilter filter = new RequestIdFilter(null);

        AtomicReference<String> requestIdSeen = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> requestIdSeen.set(MDC.get("requestId")));

        assertThat(requestIdSeen).hasValue("incoming-request");
        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo("incoming-request");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void shouldReuseTraceIdWhenAvailable() throws ServletException, IOException {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class, Mockito.RETURNS_DEEP_STUBS);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context().traceId()).thenReturn("trace-123");
        when(span.context().spanId()).thenReturn("span-456");

        RequestIdFilter filter = new RequestIdFilter(tracer);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> requestIdSeen = new AtomicReference<>();
        AtomicReference<String> traceIdSeen = new AtomicReference<>();
        AtomicReference<String> spanIdSeen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            requestIdSeen.set(MDC.get("requestId"));
            traceIdSeen.set(MDC.get("traceId"));
            spanIdSeen.set(MDC.get("spanId"));
        });

        assertThat(requestIdSeen).hasValue("trace-123");
        assertThat(traceIdSeen).hasValue("trace-123");
        assertThat(spanIdSeen).hasValue("span-456");
        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo("trace-123");
    }

    @Test
    void shouldGenerateUuidV7WhenTraceUnavailable() {
        RequestIdFilter filter = new RequestIdFilter(null);

        String generated = filter.generateUuidV7();
        UUID uuid = UUID.fromString(generated);

        assertThat(uuid.version()).isEqualTo(7);
        Assertions.assertThat(uuid.variant()).isEqualTo(2);
    }
}
