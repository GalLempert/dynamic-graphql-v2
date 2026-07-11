package sigma.console.model;

import sigma.model.Endpoint;
import sigma.model.filter.FilterConfig;
import sigma.model.filter.FilterOperator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Translates between the product-language ResourceDefinition and the
 * engine's endpoint configuration tree.
 *
 * compile():   definition -> config tree nodes (what ZooKeeper stores)
 * toDefinition(): live Endpoint -> definition (what the editor shows)
 *
 * The mapping is deliberately deterministic so a definition committed to
 * Git always produces the same tree, making diffs and drift checks exact.
 */
public final class ResourceCompiler {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Pattern FIELD_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_.]*");

    private ResourceCompiler() {
    }

    /**
     * Validates a definition and returns problems in product language.
     * An empty list means the definition is publishable.
     */
    public static List<String> validate(ResourceDefinition def) {
        List<String> problems = new ArrayList<>();

        if (def.getName() == null || def.getName().isBlank()) {
            problems.add("The resource needs a name.");
        } else if (!NAME_PATTERN.matcher(def.getName()).matches()) {
            problems.add("Resource names should be short and lowercase, using only letters, "
                    + "numbers and dashes - for example 'orders' or 'store-items'.");
        }

        ResourceDefinition.Actions a = def.getActions();
        if (!a.isRead() && !a.isAdd() && !a.isChange() && !a.isRemove()) {
            problems.add("Pick at least one action - otherwise nobody can do anything with this resource.");
        }

        validateConditions("search", def.getSearch(), problems);
        validateConditions("change targeting", def.getChangeBy(), problems);

        int pageSize = def.getFeatures().getPageSize();
        if (pageSize < 1 || pageSize > 1000) {
            problems.add("Page size should be between 1 and 1000 records.");
        }

        return problems;
    }

    private static void validateConditions(String section, Map<String, List<String>> fields, List<String> problems) {
        if (fields == null) {
            return;
        }
        String available = java.util.Arrays.stream(FriendlyOperator.values())
                .map(FriendlyOperator::getLabel)
                .collect(Collectors.joining(", "));
        fields.forEach((field, conditions) -> {
            if (field == null || !FIELD_PATTERN.matcher(field).matches()) {
                problems.add("'" + field + "' is not a valid field name for " + section + ".");
            }
            if (conditions == null || conditions.isEmpty()) {
                problems.add("Field '" + field + "' in " + section + " needs at least one condition.");
                return;
            }
            for (String condition : conditions) {
                if (FriendlyOperator.fromLabel(condition).isEmpty()) {
                    problems.add("Unknown condition '" + condition + "' on field '" + field + "' in "
                            + section + ". Available conditions: " + available + ".");
                }
            }
        });
    }

    /**
     * Compiles a definition to configuration tree nodes under the
     * endpoints base path (e.g. /dev/sigma/endpoints).
     */
    public static Map<String, byte[]> compile(ResourceDefinition def, String endpointsBasePath) {
        String base = endpointsBasePath + "/" + def.getName();
        Map<String, byte[]> nodes = new TreeMap<>();

        ResourceDefinition.Actions a = def.getActions();

        // Reads use GET; body-based search uses POST, so POST is always routed.
        List<String> methods = new ArrayList<>(List.of("GET", "POST"));
        if (a.isChange()) methods.add("PUT");
        if (a.isRemove()) methods.add("DELETE");

        List<String> writeMethods = new ArrayList<>();
        if (a.isAdd()) writeMethods.add("POST");
        if (a.isChange()) writeMethods.add("PUT");
        if (a.isRemove()) writeMethods.add("DELETE");

        put(nodes, base + "/path", "/" + def.getName());
        put(nodes, base + "/httpMethod", String.join(",", methods));
        put(nodes, base + "/databaseCollection", def.getName());
        put(nodes, base + "/type", "REST");
        put(nodes, base + "/sequenceEnabled", String.valueOf(def.getFeatures().isChangeFeed()));
        put(nodes, base + "/defaultBulkSize", String.valueOf(def.getFeatures().getPageSize()));
        if (!writeMethods.isEmpty()) {
            put(nodes, base + "/writeMethods", String.join(",", writeMethods));
        }
        if (def.getDescription() != null && !def.getDescription().isBlank()) {
            put(nodes, base + "/description", def.getDescription().trim());
        }

        compileConditions(nodes, base + "/readFilter", def.getSearch());
        compileConditions(nodes, base + "/writeFilter", def.getChangeBy());

        return nodes;
    }

    private static void compileConditions(Map<String, byte[]> nodes, String base, Map<String, List<String>> fields) {
        if (fields == null) {
            return;
        }
        fields.forEach((field, conditions) -> {
            String symbols = conditions.stream()
                    .map(label -> FriendlyOperator.fromLabel(label)
                            .orElseThrow(() -> new IllegalArgumentException("Unknown condition: " + label))
                            .getEngineOperator().getOperatorSymbol())
                    .collect(Collectors.joining(","));
            put(nodes, base + "/" + field, symbols);
        });
    }

    /**
     * Builds the editable definition from a live endpoint.
     */
    public static ResourceDefinition toDefinition(Endpoint endpoint, String description) {
        ResourceDefinition def = new ResourceDefinition();
        def.setName(endpoint.getName());
        def.setDescription(description);

        List<String> methods = List.of(endpoint.getHttpMethod().split(","));
        ResourceDefinition.Actions actions = new ResourceDefinition.Actions();
        actions.setRead(methods.stream().anyMatch(m -> m.trim().equalsIgnoreCase("GET")));
        actions.setAdd(endpoint.getAllowedWriteMethods().contains("POST"));
        actions.setChange(endpoint.getAllowedWriteMethods().contains("PUT"));
        actions.setRemove(endpoint.getAllowedWriteMethods().contains("DELETE"));
        def.setActions(actions);

        def.setSearch(toConditions(endpoint.getReadFilterConfig()));
        def.setChangeBy(toConditions(endpoint.getWriteFilterConfig()));

        ResourceDefinition.Features features = new ResourceDefinition.Features();
        features.setChangeFeed(endpoint.isSequenceEnabled());
        features.setPageSize(endpoint.getDefaultBulkSize());
        def.setFeatures(features);

        return def;
    }

    private static Map<String, List<String>> toConditions(FilterConfig filterConfig) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        if (filterConfig == null || filterConfig.getFieldOperators() == null) {
            return fields;
        }
        new TreeMap<>(filterConfig.getFieldOperators()).forEach((field, operators) -> fields.put(field,
                operators.stream()
                        .map(ResourceCompiler::toLabel)
                        .toList()));
        return fields;
    }

    private static String toLabel(FilterOperator operator) {
        return FriendlyOperator.fromEngineOperator(operator)
                .map(FriendlyOperator::getLabel)
                .orElse(operator.getOperatorSymbol());
    }

    private static void put(Map<String, byte[]> nodes, String path, String value) {
        nodes.put(path, value.getBytes(StandardCharsets.UTF_8));
    }
}
