package sigma.console.model;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A resource as product people think about it: a named collection of
 * records, the actions people may perform on it, and the fields it can
 * be searched by. This is the document stored per-resource as YAML in
 * the config repository and edited by the console UI.
 *
 * It compiles deterministically to the engine's endpoint configuration
 * (see ResourceCompiler) — the vocabulary here is deliberately free of
 * HTTP and database terms.
 */
@Getter
@Setter
public class ResourceDefinition {

    /** Short technical name, also the API path segment (e.g. "products"). */
    private String name;

    /** Optional human description shown in the console. */
    private String description;

    /** What people may do with records of this resource. */
    private Actions actions = new Actions();

    /** Searchable fields: field name -> allowed conditions (FriendlyOperator labels). */
    private Map<String, List<String>> search = new LinkedHashMap<>();

    /**
     * How records are targeted when changing or removing them.
     * Records can always be targeted by their id; entries here allow more.
     */
    private Map<String, List<String>> changeBy = new LinkedHashMap<>();

    private Features features = new Features();

    @Getter
    @Setter
    public static class Actions {
        private boolean read = true;
        private boolean add = false;
        private boolean change = false;
        private boolean remove = false;
    }

    @Getter
    @Setter
    public static class Features {
        /** Expose a change feed so other systems can follow updates. */
        private boolean changeFeed = false;
        /** Default number of records per page. */
        private int pageSize = 100;
    }
}
