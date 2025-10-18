package iaf.ofek.sigma.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Displays ASCII art banner on application startup
 * Shows service name from environment variable
 */
@Component
public class StartupBanner implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(StartupBanner.class);

    private final Environment environment;

    public StartupBanner(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String serviceName = environment.getProperty("SERVICE", "UNKNOWN");
        String banner = generateBanner(serviceName);
        logger.info("\n{}", banner);
    }

    private String generateBanner(String serviceName) {
        // ASCII art template for "X-SERVICES"
        String art = """
                
                ╔══════════════════════════════════════════════════════════════╗
                ║                                                              ║
                ║      ██╗  ██╗      ███████╗███████╗██████╗ ██╗   ██╗        ║
                ║      ╚██╗██╔╝      ██╔════╝██╔════╝██╔══██╗██║   ██║        ║
                ║       ╚███╔╝ █████╗███████╗█████╗  ██████╔╝██║   ██║        ║
                ║       ██╔██╗ ╚════╝╚════██║██╔══╝  ██╔══██╗╚██╗ ██╔╝        ║
                ║      ██╔╝ ██╗      ███████║███████╗██║  ██║ ╚████╔╝         ║
                ║      ╚═╝  ╚═╝      ╚══════╝╚══════╝╚═╝  ╚═╝  ╚═══╝          ║
                ║                                                              ║
                ║                    SERVICE: %-31s  ║
                ║                    Status: RUNNING                           ║
                ║                                                              ║
                ╚══════════════════════════════════════════════════════════════╝
                """;

        return String.format(art, serviceName.toUpperCase());
    }
}
