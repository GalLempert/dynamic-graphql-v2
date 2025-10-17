package iaf.ofek.sigma.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter(null);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesExistingHeaderValue() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, "existing-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdSeen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> requestIdSeen.set(MDC.get("requestId")));

        assertThat(requestIdSeen.get()).isEqualTo("existing-id");
        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo("existing-id");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void generatesRequestIdWhenMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdSeen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> requestIdSeen.set(MDC.get("requestId")));

        String generatedId = requestIdSeen.get();
        assertThat(generatedId).isNotBlank();
        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo(generatedId);
        assertThat(MDC.get("requestId")).isNull();
    }
}
