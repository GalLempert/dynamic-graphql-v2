package sigma.service.write.subentity;

import java.util.Map;

/**
 * Command that updates an existing sub-entity entry.
 */
class UpdateSubEntityCommand implements SubEntityCommand {

    private final Long id;
    private final Map<String, Object> attributes;

    UpdateSubEntityCommand(Long id, Map<String, Object> attributes) {
        this.id = id;
        this.attributes = attributes;
    }

    @Override
    public void apply(SubEntityCollection collection) {
        collection.update(id, attributes);
    }
}
