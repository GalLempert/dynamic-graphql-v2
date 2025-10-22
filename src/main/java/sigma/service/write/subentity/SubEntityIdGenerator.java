package sigma.service.write.subentity;

/**
 * Generates unique identifiers for sub-entity entries.
 */
public interface SubEntityIdGenerator {

    /**
     * Creates a new identifier.
     *
     * @return unique identifier value
     */
    String generate();
}
