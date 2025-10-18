package iaf.ofek.sigma.format;

import java.time.Instant;

/**
 * Strategy interface for formatting timestamps
 * 
 * Strategy Pattern: Different implementations handle different time format representations
 * This allows users to request their preferred time format via HTTP header
 */
public interface TimeFormatStrategy {
    
    /**
     * Formats an ISO-8601 timestamp string to the target format
     * 
     * @param isoTimestamp ISO-8601 timestamp string (e.g., "2025-10-18T10:30:00Z")
     * @return Formatted timestamp in the strategy's format
     */
    String format(String isoTimestamp);
}
