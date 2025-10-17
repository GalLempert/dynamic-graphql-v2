package iaf.ofek.sigma.zookeeper.util;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Utility methods for converting Zookeeper data
 */
public class ZookeeperUtils {

    private ZookeeperUtils() {
        // Utility class
    }

    /**
     * Converts byte array to String
     */
    public static Optional<String> bytesToString(byte[] data) {
        if (data == null) {
            return Optional.empty();
        }
        return Optional.of(new String(data, StandardCharsets.UTF_8));
    }

    /**
     * Converts byte array to String with default value
     */
    public static String bytesToString(byte[] data, String defaultValue) {
        return bytesToString(data).orElse(defaultValue);
    }

    /**
     * Converts byte array to Integer
     */
    public static Optional<Integer> bytesToInteger(byte[] data) {
        return bytesToString(data).map(Integer::parseInt);
    }

    /**
     * Converts byte array to Integer with default value
     */
    public static Integer bytesToInteger(byte[] data, Integer defaultValue) {
        return bytesToInteger(data).orElse(defaultValue);
    }

    /**
     * Converts byte array to Long
     */
    public static Optional<Long> bytesToLong(byte[] data) {
        return bytesToString(data).map(Long::parseLong);
    }

    /**
     * Converts byte array to Long with default value
     */
    public static Long bytesToLong(byte[] data, Long defaultValue) {
        return bytesToLong(data).orElse(defaultValue);
    }

    /**
     * Converts byte array to Boolean
     */
    public static Optional<Boolean> bytesToBoolean(byte[] data) {
        return bytesToString(data).map(Boolean::parseBoolean);
    }

    /**
     * Converts byte array to Boolean with default value
     */
    public static Boolean bytesToBoolean(byte[] data, Boolean defaultValue) {
        return bytesToBoolean(data).orElse(defaultValue);
    }

    /**
     * Converts byte array to Double
     */
    public static Optional<Double> bytesToDouble(byte[] data) {
        return bytesToString(data).map(Double::parseDouble);
    }

    /**
     * Converts byte array to Double with default value
     */
    public static Double bytesToDouble(byte[] data, Double defaultValue) {
        return bytesToDouble(data).orElse(defaultValue);
    }
}
