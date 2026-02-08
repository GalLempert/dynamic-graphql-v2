package sigma.integration;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

/**
 * Initializes test environment by setting required system properties
 * that would normally come from environment variables.
 *
 * This allows tests to run without needing to set ENV and SERVICE
 * environment variables externally.
 */
public class TestEnvironmentInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String TEST_ENV = "test";
    public static final String TEST_SERVICE = "dynamic-service";

    static {
        // Set environment variables as system properties for testing
        // These are read by ZookeeperConfigService and ZookeeperConfigProperties
        // before Spring context is fully available
        setEnvVariables();
    }

    private static void setEnvVariables() {
        // Use reflection to set environment variables for the test
        // This is needed because System.getenv() is used directly
        try {
            java.util.Map<String, String> env = System.getenv();
            java.lang.reflect.Field field = env.getClass().getDeclaredField("m");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> writableEnv = (java.util.Map<String, String>) field.get(env);
            writableEnv.put("ENV", TEST_ENV);
            writableEnv.put("SERVICE", TEST_SERVICE);
        } catch (Exception e) {
            // Fallback - try ProcessBuilder environment
            try {
                Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
                java.lang.reflect.Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
                theEnvironmentField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<String, String> theEnvironment = (java.util.Map<String, String>) theEnvironmentField.get(null);
                theEnvironment.put("ENV", TEST_ENV);
                theEnvironment.put("SERVICE", TEST_SERVICE);

                java.lang.reflect.Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
                theCaseInsensitiveEnvironmentField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<String, String> theCaseInsensitiveEnvironment = (java.util.Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
                theCaseInsensitiveEnvironment.put("ENV", TEST_ENV);
                theCaseInsensitiveEnvironment.put("SERVICE", TEST_SERVICE);
            } catch (Exception ex) {
                System.err.println("Warning: Could not set environment variables for testing. " +
                    "Tests may fail if ENV and SERVICE are not set. Error: " + ex.getMessage());
            }
        }
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        // Ensure environment is set
        setEnvVariables();

        // Add test properties
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
            applicationContext,
            "spring.main.allow-bean-definition-overriding=true"
        );
    }
}
