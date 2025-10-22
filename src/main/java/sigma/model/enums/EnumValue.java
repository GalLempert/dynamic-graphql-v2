package sigma.model.enums;

/**
 * Value definition for a dynamic enum entry.
 */
public class EnumValue {

    private final String code;
    private final String literal;

    public EnumValue(String code, String literal) {
        this.code = code;
        this.literal = literal;
    }

    public String getCode() {
        return code;
    }

    public String getLiteral() {
        return literal;
    }
}
