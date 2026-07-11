package sigma.console;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import sigma.config.properties.ZookeeperConfigProperties;
import sigma.console.model.ResourceCompiler;
import sigma.console.model.ResourceDefinition;
import sigma.controller.EndpointRegistry;
import sigma.model.Endpoint;
import sigma.zookeeper.ZookeeperConfigService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sigma Console — editing API (Phase 1).
 *
 * Publish flow: validate -> commit to Git (audit trail) -> apply to the
 * running system. Requires sigma.console.edit.enabled=true on top of the
 * read-only console flag, so production can run the explorer without the
 * editor until the approval flow (Phase 2) is in place.
 */
@RestController
@ConditionalOnProperty(name = "sigma.console.edit.enabled", havingValue = "true")
public class ConsoleEditController {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleEditController.class);

    private final EndpointRegistry endpointRegistry;
    private final ZookeeperConfigService configService;
    private final ZookeeperConfigProperties configProperties;
    private final GitConfigStore gitConfigStore;
    private final ConfigApplier configApplier;

    public ConsoleEditController(EndpointRegistry endpointRegistry,
                                 ZookeeperConfigService configService,
                                 ZookeeperConfigProperties configProperties,
                                 GitConfigStore gitConfigStore,
                                 ConfigApplier configApplier) {
        this.endpointRegistry = endpointRegistry;
        this.configService = configService;
        this.configProperties = configProperties;
        this.gitConfigStore = gitConfigStore;
        this.configApplier = configApplier;
    }

    /**
     * Every resource as an editable product-language definition.
     */
    @GetMapping("/console/api/resources")
    public List<ResourceDefinition> resources() {
        Map<String, Endpoint> byName = new TreeMap<>();
        endpointRegistry.getAllEndpoints().values()
                .forEach(endpoint -> byName.putIfAbsent(endpoint.getName(), endpoint));

        List<ResourceDefinition> result = new ArrayList<>();
        byName.values().forEach(endpoint -> result.add(ResourceCompiler.toDefinition(endpoint, descriptionOf(endpoint))));
        return result;
    }

    /**
     * Dry-run: reports problems (in product language) and previews the
     * YAML exactly as it would be committed. Changes nothing.
     */
    @PostMapping("/console/api/resources/validate")
    public Map<String, Object> validate(@RequestBody ResourceDefinition definition) {
        List<String> problems = ResourceCompiler.validate(definition);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", problems.isEmpty());
        result.put("problems", problems);
        if (problems.isEmpty()) {
            result.put("preview", gitConfigStore.toYaml(definition));
            result.put("isNew", findEndpoint(definition.getName()) == null);
        }
        return result;
    }

    /**
     * Publishes a resource: validate -> Git commit -> live apply.
     */
    @PostMapping("/console/api/resources")
    public ResponseEntity<Map<String, Object>> publish(@RequestBody PublishRequest request) {
        ResourceDefinition definition = request.resource();
        Map<String, Object> result = new LinkedHashMap<>();

        List<String> problems = ResourceCompiler.validate(definition);
        if (request.reason() == null || request.reason().isBlank()) {
            problems = new ArrayList<>(problems);
            problems.add("Please describe why you are making this change - it becomes part of the change history.");
        }
        if (!problems.isEmpty()) {
            result.put("published", false);
            result.put("problems", problems);
            return ResponseEntity.badRequest().body(result);
        }

        try {
            String commitId = gitConfigStore.save(definition, request.reason().trim(), request.author());
            Map<String, byte[]> nodes = ResourceCompiler.compile(definition, configProperties.getEndpointsBasePath());
            configApplier.apply(nodes);

            result.put("published", true);
            result.put("commit", commitId);
            result.put("problems", List.of());
            logger.info("Resource '{}' published and live (commit {})", definition.getName(), commitId.substring(0, 9));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to publish resource '{}'", definition.getName(), e);
            result.put("published", false);
            result.put("problems", List.of("Something went wrong while publishing: " + e.getMessage()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    private Endpoint findEndpoint(String name) {
        return endpointRegistry.getAllEndpoints().values().stream()
                .filter(endpoint -> endpoint.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private String descriptionOf(Endpoint endpoint) {
        return configService.getNodeDataAsString(
                configProperties.getEndpointsBasePath() + "/" + endpoint.getName() + "/description");
    }

    public record PublishRequest(ResourceDefinition resource, String reason, String author) {
    }
}
