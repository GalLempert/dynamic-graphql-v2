package sigma.integration;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Verifies the test environment is configured before the Spring context starts.
 *
 * The application reads ENV and SERVICE via System.getenv() in several places,
 * so real environment variables are required. They are supplied to the test JVM
 * by the surefire plugin configuration in pom.xml (ENV=test, SERVICE=dynamic-service).
 * Run tests through Maven, or set both variables manually when using an IDE runner.
 */
public class TestEnvironmentInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String TEST_ENV = "test";
    public static final String TEST_SERVICE = "dynamic-service";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        if (System.getenv("ENV") == null || System.getenv("SERVICE") == null) {
            throw new IllegalStateException(
                    "ENV and SERVICE environment variables must be set for integration tests. "
                    + "Maven provides them via the surefire configuration; IDE runners must set them manually.");
        }
    }
}
