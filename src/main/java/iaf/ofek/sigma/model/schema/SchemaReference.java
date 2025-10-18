package iaf.ofek.sigma.model.schema;

/**
 * Reference to a JSON Schema stored in ZooKeeper
 *
 * Structure in ZooKeeper: /{ENV}/{SERVICE}/schemas/{schemaName}
 *
 * Each endpoint can reference a schema for validation of write operations
 */
public class SchemaReference {

    private final String schemaName;
    private final boolean required;

    public SchemaReference(String schemaName, boolean required) {
        this.schemaName = schemaName;
        this.required = required;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public boolean isRequired() {
        return required;
    }

    /**
     * Returns the ZooKeeper path to this schema
     */
    public String getZkPath(String basePath) {
        return basePath + "/schemas/" + schemaName;
    }

    @Override
    public String toString() {
        return "SchemaReference{" +
                "schemaName='" + schemaName + '\'' +
                ", required=" + required +
                '}';
    }
}
