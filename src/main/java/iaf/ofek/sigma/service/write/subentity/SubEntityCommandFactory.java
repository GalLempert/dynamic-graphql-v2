package iaf.ofek.sigma.service.write.subentity;

import java.util.Map;

/**
 * Creates {@link SubEntityCommand} instances from incoming payloads.
 */
class SubEntityCommandFactory {

    SubEntityCommand createForCreate(String fieldName, Map<?, ?> payload) {
        SubEntityPayload parsed = SubEntityPayload.forCreate(fieldName, payload);
        return new CreateSubEntityCommand(parsed.myId(), parsed.attributes());
    }

    SubEntityCommand createForModify(String fieldName, Map<?, ?> payload) {
        SubEntityPayload parsed = SubEntityPayload.forModify(fieldName, payload);
        if (parsed.deleted()) {
            if (parsed.myId() == null || parsed.myId().isBlank()) {
                throw new IllegalArgumentException(
                        "Cannot delete sub-entity without myId for field '" + fieldName + "'");
            }
            return new DeleteSubEntityCommand(parsed.myId());
        }
        if (parsed.myId() == null || parsed.myId().isBlank()) {
            return new CreateSubEntityCommand(null, parsed.attributes());
        }
        return new UpdateSubEntityCommand(parsed.myId(), parsed.attributes());
    }
}
