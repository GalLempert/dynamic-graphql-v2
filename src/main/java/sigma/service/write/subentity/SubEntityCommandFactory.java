package sigma.service.write.subentity;

import java.util.Map;

/**
 * Creates {@link SubEntityCommand} instances from incoming payloads.
 */
class SubEntityCommandFactory {

    SubEntityCommand createForCreate(String fieldName, Map<?, ?> payload) {
        SubEntityPayload parsed = SubEntityPayload.forCreate(fieldName, payload);
        return new CreateSubEntityCommand(parsed.id(), parsed.attributes());
    }

    SubEntityCommand createForModify(String fieldName, Map<?, ?> payload) {
        SubEntityPayload parsed = SubEntityPayload.forModify(fieldName, payload);
        if (parsed.deleted()) {
            if (parsed.id() == null) {
                throw new IllegalArgumentException(
                        "Cannot delete sub-entity without id for field '" + fieldName + "'");
            }
            return new DeleteSubEntityCommand(parsed.id());
        }
        if (parsed.id() == null) {
            return new CreateSubEntityCommand(null, parsed.attributes());
        }
        return new UpdateSubEntityCommand(parsed.id(), parsed.attributes());
    }
}
