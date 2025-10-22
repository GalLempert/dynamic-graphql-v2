package sigma.service.write.subentity;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * {@link SubEntityIdGenerator} implementation that produces UUID identifiers.
 */
@Component
public class UuidSubEntityIdGenerator implements SubEntityIdGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
