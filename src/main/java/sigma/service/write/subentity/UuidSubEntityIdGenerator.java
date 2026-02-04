package sigma.service.write.subentity;

import java.util.UUID;

/**
 * {@link SubEntityIdGenerator} implementation that produces UUID identifiers.
 * Note: Not annotated with @Component - use SequenceSubEntityIdGenerator as default.
 * This class is kept for backward compatibility if UUID-based IDs are needed.
 */
public class UuidSubEntityIdGenerator implements SubEntityIdGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
