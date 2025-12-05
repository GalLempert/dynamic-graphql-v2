package sigma.format;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Service for formatting timestamps in response documents
 *
 * Single Responsibility: Apply time format transformations to response data
 * Uses Strategy Pattern via TimeFormatRegistry
 */
@Service
public class ResponseTimeFormatter {

    private static final Logger logger = LoggerFactory.getLogger(ResponseTimeFormatter.class);

    // Spring Data audit field names
    private static final String CREATED_AT = "createdAt";
    private static final String LAST_MODIFIED_AT = "lastModifiedAt";

    private final TimeFormatRegistry registry;

    public ResponseTimeFormatter(TimeFormatRegistry registry) {
        this.registry = registry;
    }

    /**
     * Formats all timestamps in a single document according to the current request's preference
     *
     * @param document Document containing timestamp fields
     * @return Document with formatted timestamps
     */
    public Map<String, Object> formatDocument(Map<String, Object> document) {
        if (document == null || document.isEmpty()) {
            return document;
        }

        String requestedFormat = TimeFormatContext.getFormat();
        TimeFormatStrategy strategy = registry.getStrategy(requestedFormat);

        // Format Spring Data audit timestamp fields if present
        formatField(document, CREATED_AT, strategy);
        formatField(document, LAST_MODIFIED_AT, strategy);

        return document;
    }
    
    /**
     * Formats all timestamps in a list of documents
     *
     * @param documents List of documents (supports BSON Document and Map)
     * @return List with formatted timestamps
     */
    public <T extends Map<String, Object>> List<T> formatDocuments(List<T> documents) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }

        documents.forEach(this::formatDocument);
        return documents;
    }
    
    /**
     * Formats a specific field in a document if it exists and is a timestamp
     */
    private void formatField(Map<String, Object> document, String fieldName, TimeFormatStrategy strategy) {
        Object value = document.get(fieldName);

        String timestampStr = null;

        if (value instanceof String str) {
            timestampStr = str;
        } else if (value instanceof Date date) {
            timestampStr = date.toInstant().toString();
        } else if (value instanceof Instant instant) {
            timestampStr = instant.toString();
        }

        if (timestampStr != null) {
            try {
                String formatted = strategy.format(timestampStr);
                document.put(fieldName, formatted);
            } catch (Exception e) {
                logger.warn("Failed to format timestamp field '{}' with value '{}': {}",
                        fieldName, timestampStr, e.getMessage());
                // Leave original value on error
            }
        }
    }
}
