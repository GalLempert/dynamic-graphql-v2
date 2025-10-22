package sigma.service.write.subentity;

import java.util.Map;

/**
 * Command that creates a new sub-entity entry.
 */
class CreateSubEntityCommand implements SubEntityCommand {

    private final String requestedId;
    private final Map<String, Object> attributes;

    CreateSubEntityCommand(String requestedId, Map<String, Object> attributes) {
        this.requestedId = requestedId;
        this.attributes = attributes;
    }

    @Override
    public void apply(SubEntityCollection collection) {
        collection.addNew(requestedId, attributes);
    }
}
