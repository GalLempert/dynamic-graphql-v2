package sigma.format;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for ResponseTimeFormatter - Verifies timestamp formatting in responses
 *
 * Key features tested:
 * - Uses TimeFormatStrategy from registry based on request context
 * - Formats createdAt and lastModifiedAt Spring Data audit fields
 * - Handles missing timestamps gracefully
 * - Works with both single documents and lists
 */
@ExtendWith(MockitoExtension.class)
class ResponseTimeFormatterTest {

    // Spring Data audit field names
    private static final String CREATED_AT = "createdAt";
    private static final String LAST_MODIFIED_AT = "lastModifiedAt";

    @Mock
    private TimeFormatRegistry registry;

    @Mock
    private TimeFormatStrategy strategy;

    private ResponseTimeFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new ResponseTimeFormatter(registry);
        when(registry.getStrategy(anyString())).thenReturn(strategy);
    }

    @AfterEach
    void tearDown() {
        // Clean up ThreadLocal to prevent test pollution
        TimeFormatContext.clear();
    }

    /**
     * Tests that formatter uses the strategy from registry based on context
     */
    @Test
    void testUsesStrategyFromRegistryBasedOnContext() {
        // Given: Request context with specific format
        TimeFormatContext.setFormat("UNIX");
        Map<String, Object> document = new HashMap<>();
        document.put(CREATED_AT, "2025-01-15T10:30:00Z");

        when(strategy.format("2025-01-15T10:30:00Z")).thenReturn("1736938200");

        // When: Format document
        formatter.formatDocument(document);

        // Then: Should use registry to get strategy for "UNIX"
        verify(registry).getStrategy("UNIX");
        verify(strategy).format("2025-01-15T10:30:00Z");
    }

    /**
     * Tests formatting of createdAt field
     */
    @Test
    void testFormatsCreatedAtField() {
        // Given: Document with createdAt
        TimeFormatContext.setFormat("ISO-8601");
        Map<String, Object> document = new HashMap<>();
        document.put(CREATED_AT, "2025-01-15T10:30:00Z");
        document.put("name", "Alice");

        when(strategy.format("2025-01-15T10:30:00Z")).thenReturn("2025-01-15T10:30:00.000Z");

        // When: Format document
        Map<String, Object> result = formatter.formatDocument(document);

        // Then: createdAt should be formatted
        assertEquals("2025-01-15T10:30:00.000Z", result.get(CREATED_AT));
        assertEquals("Alice", result.get("name"));  // Other fields unchanged
    }

    /**
     * Tests formatting of updatedAt field
     */
    @Test
    void testFormatsUpdatedAtField() {
        // Given: Document with updatedAt
        TimeFormatContext.setFormat("UNIX-MILLIS");
        Map<String, Object> document = new HashMap<>();
        document.put(LAST_MODIFIED_AT, "2025-01-15T12:45:30Z");

        when(strategy.format("2025-01-15T12:45:30Z")).thenReturn("1736946330000");

        // When: Format document
        Map<String, Object> result = formatter.formatDocument(document);

        // Then: updatedAt should be formatted
        assertEquals("1736946330000", result.get(LAST_MODIFIED_AT));
    }

    /**
     * Tests formatting of both createdAt and updatedAt
     */
    @Test
    void testFormatsBothAuditFields() {
        // Given: Document with both timestamps
        TimeFormatContext.setFormat("RFC-3339");
        Map<String, Object> document = new HashMap<>();
        document.put(CREATED_AT, "2025-01-15T10:00:00Z");
        document.put(LAST_MODIFIED_AT, "2025-01-15T11:00:00Z");

        when(strategy.format("2025-01-15T10:00:00Z")).thenReturn("2025-01-15T10:00:00+00:00");
        when(strategy.format("2025-01-15T11:00:00Z")).thenReturn("2025-01-15T11:00:00+00:00");

        // When: Format document
        Map<String, Object> result = formatter.formatDocument(document);

        // Then: Both should be formatted
        assertEquals("2025-01-15T10:00:00+00:00", result.get(CREATED_AT));
        assertEquals("2025-01-15T11:00:00+00:00", result.get(LAST_MODIFIED_AT));
        verify(strategy, times(2)).format(anyString());
    }

    /**
     * Tests that documents without timestamps are handled gracefully
     */
    @Test
    void testHandlesDocumentsWithoutTimestamps() {
        // Given: Document without audit fields
        TimeFormatContext.setFormat("ISO-8601");
        Map<String, Object> document = new HashMap<>();
        document.put("name", "Bob");
        document.put("age", 30);

        // When: Format document
        Map<String, Object> result = formatter.formatDocument(document);

        // Then: Should not call strategy, document unchanged
        verify(strategy, never()).format(anyString());
        assertEquals("Bob", result.get("name"));
        assertEquals(30, result.get("age"));
    }

    /**
     * Tests that null or empty documents are handled gracefully
     */
    @Test
    void testHandlesNullAndEmptyDocuments() {
        // Given: Null and empty documents
        TimeFormatContext.setFormat("ISO-8601");

        // When/Then: Should not throw, return as-is
        assertNull(formatter.formatDocument(null));
        assertEquals(0, formatter.formatDocument(new HashMap<>()).size());
        verify(strategy, never()).format(anyString());
    }

    /**
     * Tests formatting errors are logged but don't break response
     */
    @Test
    void testFormattingErrorsLeavesOriginalValue() {
        // Given: Strategy throws exception
        TimeFormatContext.setFormat("ISO-8601");
        Map<String, Object> document = new HashMap<>();
        document.put(CREATED_AT, "invalid-timestamp");

        when(strategy.format("invalid-timestamp")).thenThrow(new RuntimeException("Parse error"));

        // When: Format document
        Map<String, Object> result = formatter.formatDocument(document);

        // Then: Should keep original value on error
        assertEquals("invalid-timestamp", result.get(CREATED_AT));
    }

    /**
     * Tests formatting list of documents
     */
    @Test
    void testFormatsListOfDocuments() {
        // Given: List of documents with timestamps
        TimeFormatContext.setFormat("UNIX");
        Map<String, Object> doc1 = new HashMap<>();
        doc1.put(CREATED_AT, "2025-01-15T10:00:00Z");

        Map<String, Object> doc2 = new HashMap<>();
        doc2.put(LAST_MODIFIED_AT, "2025-01-15T11:00:00Z");

        List<Map<String, Object>> documents = List.of(doc1, doc2);

        when(strategy.format("2025-01-15T10:00:00Z")).thenReturn("1736938800");
        when(strategy.format("2025-01-15T11:00:00Z")).thenReturn("1736942400");

        // When: Format documents
        List<Map<String, Object>> result = formatter.formatDocuments(documents);

        // Then: All documents should be formatted
        assertEquals(2, result.size());
        assertEquals("1736938800", result.get(0).get(CREATED_AT));
        assertEquals("1736942400", result.get(1).get(LAST_MODIFIED_AT));
        verify(strategy, times(2)).format(anyString());
    }

    /**
     * Tests that null or empty lists are handled gracefully
     */
    @Test
    void testHandlesNullAndEmptyLists() {
        // Given: Null and empty lists
        TimeFormatContext.setFormat("ISO-8601");

        // When/Then: Should not throw, return as-is
        assertNull(formatter.formatDocuments(null));
        assertEquals(0, formatter.formatDocuments(List.of()).size());
        verify(strategy, never()).format(anyString());
    }

    /**
     * Tests that non-string timestamp values are ignored
     */
    @Test
    void testIgnoresNonStringTimestamps() {
        // Given: Document with non-string timestamp field
        TimeFormatContext.setFormat("ISO-8601");
        Map<String, Object> document = new HashMap<>();
        document.put(CREATED_AT, 1736938200);  // Integer, not String

        // When: Format document
        Map<String, Object> result = formatter.formatDocument(document);

        // Then: Should not attempt to format, leave as-is
        assertEquals(1736938200, result.get(CREATED_AT));
        verify(strategy, never()).format(anyString());
    }
}
