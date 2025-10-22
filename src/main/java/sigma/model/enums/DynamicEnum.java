package sigma.model.enums;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a dynamically managed enumeration definition fetched from the Enum service.
 */
public class DynamicEnum {

    private final String name;
    private final Map<String, EnumValue> valuesByCode;

    public DynamicEnum(String name, Map<String, EnumValue> valuesByCode) {
        this.name = name;
        this.valuesByCode = Collections.unmodifiableMap(new LinkedHashMap<>(valuesByCode));
    }

    public String getName() {
        return name;
    }

    public Map<String, EnumValue> getValuesByCode() {
        return valuesByCode;
    }

    public Optional<EnumValue> getValue(String code) {
        return Optional.ofNullable(valuesByCode.get(code));
    }
}
