package sigma.model.enums;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the location of an enum-backed field within a JSON document using a list of path segments.
 */
public class EnumFieldPointer {

    private final List<Segment> segments;

    public EnumFieldPointer(List<Segment> segments) {
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public interface Segment {
    }

    public record PropertySegment(String name) implements Segment {
    }

    public enum ArraySegment implements Segment {
        INSTANCE
    }
}
