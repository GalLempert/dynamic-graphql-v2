package sigma.config.local;

import org.springframework.context.ApplicationEventPublisher;
import sigma.zookeeper.ZookeeperWatcher;

/**
 * ZookeeperWatcher replacement for local dev mode.
 * The local configuration file is static, so there is nothing to watch.
 */
public class NoOpZookeeperWatcher extends ZookeeperWatcher {

    public NoOpZookeeperWatcher(ApplicationEventPublisher eventPublisher) {
        super(null, eventPublisher);
    }

    @Override
    public void watchNode(String path) {
        // Local config is static - nothing to watch
    }

    @Override
    public void watchTree(String rootPath) {
        // Local config is static - nothing to watch
    }
}
