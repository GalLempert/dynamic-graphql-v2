package sigma.model.enums;

/**
 * Associates an enum-backed schema field pointer with the enum name required for lookups.
 */
public class EnumFieldBinding {

    private final EnumFieldPointer pointer;
    private final String enumName;

    public EnumFieldBinding(EnumFieldPointer pointer, String enumName) {
        this.pointer = pointer;
        this.enumName = enumName;
    }

    public EnumFieldPointer getPointer() {
        return pointer;
    }

    public String getEnumName() {
        return enumName;
    }
}
