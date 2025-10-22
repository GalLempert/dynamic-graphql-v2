package sigma.format;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Registry for time format strategies
 * 
 * Strategy Pattern: Maps header values to concrete formatter implementations
 * Uses standard Java DateTimeFormatter instead of hardcoding formats
 */
@Service
public class TimeFormatRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(TimeFormatRegistry.class);
    
    private final Map<String, TimeFormatStrategy> strategies;
    private final TimeFormatStrategy defaultStrategy;
    
    public TimeFormatRegistry() {
        // Register standard formats using Java's DateTimeFormatter
        this.strategies = Map.of(
                "ISO-8601", ConfigurableTimeFormatter.iso8601(),
                "ISO_INSTANT", ConfigurableTimeFormatter.iso8601(),
                "RFC-3339", ConfigurableTimeFormatter.rfc3339(),
                "ISO_OFFSET_DATE_TIME", ConfigurableTimeFormatter.rfc3339(),
                "UNIX", ConfigurableTimeFormatter.unixSeconds(),
                "UNIX-MILLIS", ConfigurableTimeFormatter.unixMillis(),
                "BASIC_ISO_DATE", ConfigurableTimeFormatter.basicIsoDate(),
                "ISO_LOCAL_DATE", ConfigurableTimeFormatter.of(DateTimeFormatter.ISO_LOCAL_DATE),
                "ISO_LOCAL_DATE_TIME", ConfigurableTimeFormatter.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
        
        this.defaultStrategy = strategies.get("ISO-8601");
        
        logger.info("Registered {} time format strategies: {}", 
                strategies.size(), 
                strategies.keySet());
    }
    
    /**
     * Gets the appropriate strategy for the given format name
     * Returns default (ISO-8601) if format not found
     * 
     * @param formatName Format name from header (case-insensitive)
     * @return TimeFormatStrategy instance
     */
    public TimeFormatStrategy getStrategy(String formatName) {
        if (formatName == null || formatName.isBlank()) {
            return defaultStrategy;
        }
        
        TimeFormatStrategy strategy = strategies.get(formatName.toUpperCase());
        
        if (strategy == null) {
            logger.warn("Unknown time format requested: '{}', using default ISO-8601", formatName);
            return defaultStrategy;
        }
        
        return strategy;
    }
    
    /**
     * Returns all registered format names for documentation purposes
     */
    public Map<String, TimeFormatStrategy> getAllStrategies() {
        return strategies;
    }
    
    /**
     * Returns the default strategy (ISO-8601)
     */
    public TimeFormatStrategy getDefaultStrategy() {
        return defaultStrategy;
    }
}
