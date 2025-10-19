package iaf.ofek.sigma.service.write.subentity;

import java.util.Map;

/**
 * Command that updates an existing sub-entity entry.
 */
class UpdateSubEntityCommand implements SubEntityCommand {

    private final String myId;
    private final Map<String, Object> attributes;

    UpdateSubEntityCommand(String myId, Map<String, Object> attributes) {
        this.myId = myId;
        this.attributes = attributes;
    }

    @Override
    public void apply(SubEntityCollection collection) {
        collection.update(myId, attributes);
    }
}
