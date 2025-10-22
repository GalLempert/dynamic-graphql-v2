package sigma.service.enums;

import sigma.config.properties.ZookeeperConfigProperties;
import sigma.model.enums.DynamicEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Registry that caches enum definitions fetched from the external enum manager service.
 */
@Service
public class EnumRegistry {

    private static final Logger logger = LoggerFactory.getLogger(EnumRegistry.class);

    private final EnumServiceClient client;
    private final ZookeeperConfigProperties configProperties;
    private final AtomicReference<Map<String, DynamicEnum>> enums = new AtomicReference<>(Map.of());
    private final List<EnumRegistryListener> listeners = new CopyOnWriteArrayList<>();

    public EnumRegistry(EnumServiceClient client, ZookeeperConfigProperties configProperties) {
        this.client = client;
        this.configProperties = configProperties;
    }

    @PostConstruct
    public void initialize() {
        boolean success = reloadEnums();
        if (!success && configProperties.shouldFailOnEnumLoadFailure()) {
            throw new IllegalStateException("Failed to load enum definitions from enum service");
        }
    }

    @Scheduled(fixedDelayString = "#{@enumSchedulerProperties.refreshInterval}")
    public void scheduledReload() {
        reloadEnums();
    }

    public Optional<DynamicEnum> getEnum(String name) {
        return Optional.ofNullable(enums.get().get(name));
    }

    public Map<String, DynamicEnum> getAll() {
        return enums.get();
    }

    public void registerListener(EnumRegistryListener listener) {
        listeners.add(listener);
    }

    private boolean reloadEnums() {
        String enumUrl = configProperties.getEnumServiceUrl();
        if (enumUrl == null || enumUrl.isBlank()) {
            logger.warn("Enum service URL is not configured in ZooKeeper");
            enums.set(Map.of());
            return false;
        }

        try {
            List<DynamicEnum> fetched = client.fetchEnums(enumUrl);
            Map<String, DynamicEnum> newMap = fetched.stream()
                    .collect(Collectors.toUnmodifiableMap(DynamicEnum::getName, e -> e));
            enums.set(newMap);
            logger.info("Loaded {} enums from enum service", newMap.size());
            listeners.forEach(EnumRegistryListener::onEnumsReloaded);
            return true;
        } catch (RestClientException e) {
            logger.error("Failed to reload enums from enum service", e);
            return false;
        }
    }
}
