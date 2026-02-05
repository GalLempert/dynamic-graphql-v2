package sigma.service.write.subentity;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link SubEntityIdGenerator} implementation that produces sequential numeric identifiers.
 * Uses an AtomicLong to generate unique sequence numbers.
 * The sequence starts from the current timestamp to ensure uniqueness across restarts.
 */
@Component
public class SequenceSubEntityIdGenerator implements SubEntityIdGenerator {

    private final AtomicLong sequence;

    public SequenceSubEntityIdGenerator() {
        // Initialize with current time millis to ensure uniqueness across restarts
        this.sequence = new AtomicLong(System.currentTimeMillis());
    }

    @Override
    public Long generate() {
        return sequence.incrementAndGet();
    }
}
