package sigma.model.enums;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents the location of an enum-backed field within a JSON document using a list of path segments.
 * Each segment type knows how to traverse its own structure (polymorphic dispatch, no instanceof).
 */
public class EnumFieldPointer {

    private final List<Segment> segments;

    public EnumFieldPointer(List<Segment> segments) {
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
    }

    public List<Segment> getSegments() {
        return segments;
    }

    /**
     * Segment of a path through a JSON document.
     * Each segment knows how to traverse its portion of the document tree.
     */
    public sealed interface Segment permits PropertySegment, ArraySegment {
        /**
         * Traverses this segment of the document, applying the continuation to the next level.
         *
         * @param current the current document node
         * @param continuation function to apply on the next level's value, returning the transformed value
         * @return the (possibly modified) current node
         */
        Object traverse(Object current, Function<Object, Object> continuation);
    }

    public record PropertySegment(String name) implements Segment {
        @Override
        public Object traverse(Object current, Function<Object, Object> continuation) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return current;
            }
            Object value = currentMap.get(name);
            if (value == null) {
                return current;
            }
            Object transformed = continuation.apply(value);
            if (transformed != value) {
                @SuppressWarnings("unchecked")
                Map<String, Object> writableMap = (Map<String, Object>) currentMap;
                writableMap.put(name, transformed);
            }
            return current;
        }
    }

    public enum ArraySegment implements Segment {
        INSTANCE;

        @Override
        public Object traverse(Object current, Function<Object, Object> continuation) {
            if (!(current instanceof List<?> list)) {
                return current;
            }
            for (int i = 0; i < list.size(); i++) {
                Object element = list.get(i);
                Object transformed = continuation.apply(element);
                if (!Objects.equals(transformed, element)) {
                    @SuppressWarnings("unchecked")
                    List<Object> mutable = (List<Object>) list;
                    mutable.set(i, transformed);
                }
            }
            return current;
        }
    }
}
