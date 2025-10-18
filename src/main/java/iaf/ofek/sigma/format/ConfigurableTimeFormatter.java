package iaf.ofek.sigma.format;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * Configurable time formatter using standard Java DateTimeFormatter
 * 
 * Supports:
 * - Standard Java DateTimeFormatter constants (ISO_INSTANT, ISO_OFFSET_DATE_TIME, etc.)
 * - Unix timestamps (seconds/milliseconds)
 * - Custom patterns
 */
public class ConfigurableTimeFormatter implements TimeFormatStrategy {

    private final Function<Instant, String> formatter;

    private ConfigurableTimeFormatter(Function<Instant, String> formatter) {
        this.formatter = formatter;
    }

    @Override
    public String format(String isoTimestamp) {
        Instant instant = Instant.parse(isoTimestamp);
        return formatter.apply(instant);
    }

    // Factory methods for standard formats

    public static ConfigurableTimeFormatter iso8601() {
        return new ConfigurableTimeFormatter(DateTimeFormatter.ISO_INSTANT::format);
    }

    public static ConfigurableTimeFormatter rfc3339() {
        return new ConfigurableTimeFormatter(
                instant -> DateTimeFormatter.ISO_OFFSET_DATE_TIME
                        .withZone(ZoneOffset.UTC)
                        .format(instant)
        );
    }

    public static ConfigurableTimeFormatter unixSeconds() {
        return new ConfigurableTimeFormatter(
                instant -> String.valueOf(instant.getEpochSecond())
        );
    }

    public static ConfigurableTimeFormatter unixMillis() {
        return new ConfigurableTimeFormatter(
                instant -> String.valueOf(instant.toEpochMilli())
        );
    }

    public static ConfigurableTimeFormatter basicIsoDate() {
        return new ConfigurableTimeFormatter(DateTimeFormatter.BASIC_ISO_DATE::format);
    }

    /**
     * Create formatter with custom DateTimeFormatter pattern
     * 
     * @param pattern DateTimeFormatter pattern (e.g., "yyyy-MM-dd HH:mm:ss")
     * @return ConfigurableTimeFormatter instance
     */
    public static ConfigurableTimeFormatter custom(String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern).withZone(ZoneOffset.UTC);
        return new ConfigurableTimeFormatter(formatter::format);
    }

    /**
     * Create formatter with standard DateTimeFormatter constant
     * 
     * @param formatter Java DateTimeFormatter constant
     * @return ConfigurableTimeFormatter instance
     */
    public static ConfigurableTimeFormatter of(DateTimeFormatter formatter) {
        return new ConfigurableTimeFormatter(formatter::format);
    }
}
