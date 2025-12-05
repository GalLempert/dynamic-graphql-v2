package sigma.format;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResponseTimeFormatterBugTest {

    @Mock
    private TimeFormatRegistry registry;

    @Mock
    private TimeFormatStrategy strategy;

    private ResponseTimeFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new ResponseTimeFormatter(registry);
        // Lenient stubs to avoid UnnecessaryStubbingException in setup if not used
        lenient().when(registry.getStrategy(any())).thenReturn(strategy);
    }

    @Test
    void testShouldFormatDateObject() {
        // Given: Document with Date object
        Map<String, Object> document = new HashMap<>();
        // Fixed date: 2025-10-18T10:30:00Z -> 1760783400000 ms
        Date fixedDate = new Date(1760783400000L);
        document.put("createdAt", fixedDate);

        String isoString = fixedDate.toInstant().toString(); // "2025-10-18T10:30:00Z"

        // Mock strategy to return a formatted string
        when(strategy.format(isoString)).thenReturn("FORMATTED_DATE");
        when(registry.getStrategy(any())).thenReturn(strategy);

        // When: Format document
        formatter.formatDocument(document);

        // Then: The value SHOULD be a String now
        assertEquals("FORMATTED_DATE", document.get("createdAt"));
        verify(strategy).format(isoString);
    }

    @Test
    void testShouldFormatInstantObject() {
        // Given: Document with Instant object
        Map<String, Object> document = new HashMap<>();
        Instant now = Instant.now();
        document.put("lastModifiedAt", now);

        // Mock strategy
        when(strategy.format(now.toString())).thenReturn("FORMATTED_INSTANT");
        when(registry.getStrategy(any())).thenReturn(strategy);

        // When: Format document
        formatter.formatDocument(document);

        // Then: The value SHOULD be a String now
        assertEquals("FORMATTED_INSTANT", document.get("lastModifiedAt"));
        verify(strategy).format(now.toString());
    }
}
