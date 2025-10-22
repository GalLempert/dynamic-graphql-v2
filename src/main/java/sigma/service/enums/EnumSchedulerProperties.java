package sigma.service.enums;

import sigma.config.properties.ZookeeperConfigProperties;
import org.springframework.stereotype.Component;

/**
 * Provides scheduling configuration for enum refresh using ZooKeeper-backed settings.
 */
@Component("enumSchedulerProperties")
public class EnumSchedulerProperties {

    private final ZookeeperConfigProperties configProperties;

    public EnumSchedulerProperties(ZookeeperConfigProperties configProperties) {
        this.configProperties = configProperties;
    }

    public String getRefreshInterval() {
        long interval = configProperties.getEnumRefreshIntervalMillis();
        return String.valueOf(Math.max(interval, 1_000L));
    }
}
