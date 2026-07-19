package sigma.console;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import sigma.config.properties.ZookeeperConfigProperties;
import sigma.console.model.ResourceCompiler;
import sigma.console.model.ResourceDefinition;
import sigma.controller.EndpointRegistry;
import sigma.model.Endpoint;
import sigma.model.filter.FilterConfig;
import sigma.model.filter.FilterOperator;
import sigma.persistence.dialect.DatabaseDialect;
import sigma.zookeeper.ZookeeperConfigService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sigma Console — read-only control-plane API (Phase 0).
 *
 * Exposes the live endpoint configuration so the console UI can render
 * a human-friendly explorer. Strictly read-only: editing flows go through
 * Git in later phases, never directly through this API.
 *
 * Disabled by default; enable with sigma.console.enabled=true
 * (on by default only in the local dev profile).
 */
@RestController
@ConditionalOnProperty(name = "sigma.console.enabled", havingValue = "true")
public class ConsoleController {

    private static final List<String> SECRET_MARKERS = List.of("password", "secret", "token", "credential");

    private final EndpointRegistry endpointRegistry;
    private final ZookeeperConfigService configService;
    private final ZookeeperConfigProperties configProperties;
    private final DatabaseDialect databaseDialect;
    private final boolean editEnabled;

    public ConsoleController(EndpointRegistry endpointRegistry,
                             ZookeeperConfigService configService,
                             ZookeeperConfigProperties configProperties,
                             DatabaseDialect databaseDialect,
                             @Value("${sigma.console.edit.enabled:false}") boolean editEnabled) {
        this.endpointRegistry = endpointRegistry;
        this.configService = configService;
        this.configProperties = configProperties;
        this.databaseDialect = databaseDialect;
        this.editEnabled = editEnabled;
    }

    /**
     * Service identity and runtime facts for the console header.
     */
    @GetMapping("/console/api/info")
    public Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("env", System.getenv("ENV"));
        info.put("service", System.getenv("SERVICE"));
        info.put("apiPrefix", configProperties.getApiPrefix());
        info.put("databaseType", databaseDialect.getType().name());
        info.put("configNodeCount", configService.getAllConfiguration().size());
        info.put("editable", editEnabled);
        return info;
    }

    /**
     * Every resource as a product-language definition.
     * Available in read-only mode too — only publishing requires the
     * edit flag (see ConsoleEditController).
     */
    @GetMapping("/console/api/resources")
    public List<ResourceDefinition> resources() {
        Map<String, Endpoint> byName = new TreeMap<>();
        endpointRegistry.getAllEndpoints().values()
                .forEach(endpoint -> byName.putIfAbsent(endpoint.getName(), endpoint));

        List<ResourceDefinition> result = new ArrayList<>();
        byName.values().forEach(endpoint -> result.add(
                ResourceCompiler.toDefinition(endpoint, descriptionOf(endpoint))));
        return result;
    }

    private String descriptionOf(Endpoint endpoint) {
        return configService.getNodeDataAsString(
                configProperties.getEndpointsBasePath() + "/" + endpoint.getName() + "/description");
    }

    /**
     * All registered endpoints, one entry per logical endpoint
     * (multi-method endpoints are registered under several routing keys
     * but reported once here).
     */
    @GetMapping("/console/api/endpoints")
    public List<Map<String, Object>> endpoints() {
        Map<String, Endpoint> byName = new TreeMap<>();
        endpointRegistry.getAllEndpoints().values()
                .forEach(endpoint -> byName.putIfAbsent(endpoint.getName(), endpoint));

        List<Map<String, Object>> result = new ArrayList<>();
        byName.values().forEach(endpoint -> result.add(describe(endpoint)));
        return result;
    }

    /**
     * The raw configuration tree (leaf path -> value), with secret-looking
     * values redacted. This is the "declared state" view for drift checks
     * in later phases.
     */
    @GetMapping("/console/api/config")
    public Map<String, String> configTree() {
        Map<String, String> tree = new TreeMap<>();
        configService.getAllConfiguration().forEach((path, data) -> {
            String value = data != null ? new String(data, StandardCharsets.UTF_8) : "";
            tree.put(path, isSecretPath(path) ? "•••••" : value);
        });
        return tree;
    }

    private Map<String, Object> describe(Endpoint endpoint) {
        Map<String, Object> desc = new LinkedHashMap<>();
        desc.put("name", endpoint.getName());
        desc.put("path", endpoint.getPath());
        desc.put("methods", List.of(endpoint.getHttpMethod().split(",")));
        desc.put("collection", endpoint.getDatabaseCollection());
        desc.put("type", endpoint.getType().name());
        desc.put("sequenceEnabled", endpoint.isSequenceEnabled());
        desc.put("defaultBulkSize", endpoint.getDefaultBulkSize());
        desc.put("writeMethods", endpoint.getAllowedWriteMethods());
        desc.put("readFilter", describeFilter(endpoint.getReadFilterConfig()));
        desc.put("writeFilter", describeFilter(endpoint.getWriteFilterConfig()));
        desc.put("subEntities", endpoint.getSubEntities());
        desc.put("fatherDocument", endpoint.getFatherDocument());
        desc.put("schema", endpoint.getSchemaReference() != null
                ? Map.of("name", endpoint.getSchemaReference().getSchemaName(),
                         "required", endpoint.getSchemaReference().isRequired())
                : null);
        return desc;
    }

    private Map<String, Object> describeFilter(FilterConfig filterConfig) {
        if (filterConfig == null) {
            return Map.of("enabled", false, "fields", Map.of());
        }
        Map<String, List<String>> fields = new TreeMap<>();
        filterConfig.getFieldOperators().forEach((field, operators) ->
                fields.put(field, operators.stream().map(FilterOperator::getOperatorSymbol).toList()));
        Map<String, Object> desc = new LinkedHashMap<>();
        desc.put("enabled", filterConfig.isEnabled());
        desc.put("fields", fields);
        return desc;
    }

    private boolean isSecretPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return SECRET_MARKERS.stream().anyMatch(lower::contains);
    }
}
