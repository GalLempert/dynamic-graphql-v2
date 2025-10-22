package sigma.config.properties;

import java.util.Map;
import java.util.Optional;

/**
 * Interface for accessing configuration properties
 */
public interface ConfigProperties {

    /**
     * Gets a property value as a String
     */
    Optional<String> getString(String key);

    /**
     * Gets a property value as a String with a default value
     */
    String getString(String key, String defaultValue);

    /**
     * Gets a property value as an Integer
     */
    Optional<Integer> getInteger(String key);

    /**
     * Gets a property value as an Integer with a default value
     */
    Integer getInteger(String key, Integer defaultValue);

    /**
     * Gets a property value as a Boolean
     */
    Optional<Boolean> getBoolean(String key);

    /**
     * Gets a property value as a Boolean with a default value
     */
    Boolean getBoolean(String key, Boolean defaultValue);

    /**
     * Gets raw byte data for a property
     */
    Optional<byte[]> getRawData(String key);

    /**
     * Gets all properties
     */
    Map<String, String> getAllProperties();

    /**
     * Checks if a property exists
     */
    boolean hasProperty(String key);
}
