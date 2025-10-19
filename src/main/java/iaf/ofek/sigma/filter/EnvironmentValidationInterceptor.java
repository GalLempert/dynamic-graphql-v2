package iaf.ofek.sigma.filter;

import iaf.ofek.sigma.config.properties.ZookeeperConfigProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Validates that incoming requests belong to the configured environment when enabled.
 * Also ensures every response includes the environment header.
 */
@Component
public class EnvironmentValidationInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentValidationInterceptor.class);
    private static final String ENV_HEADER = "env";

    private final ZookeeperConfigProperties configProperties;
    private final String environment;

    public EnvironmentValidationInterceptor(ZookeeperConfigProperties configProperties) {
        this.configProperties = configProperties;
        String env = System.getenv("ENV");
        if (env == null || env.isBlank()) {
            throw new IllegalStateException("ENV environment variable is required");
        }
        this.environment = env;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        response.setHeader(ENV_HEADER, environment);

        if (!configProperties.isEnvValidationEnabled()) {
            return true;
        }

        String requestEnvironment = request.getHeader(ENV_HEADER);
        if (requestEnvironment != null && environment.equalsIgnoreCase(requestEnvironment)) {
            return true;
        }

        logger.error("Environment validation failed for request {} {}. Expected env header '{}', received '{}'",
                request.getMethod(), request.getRequestURI(), environment, requestEnvironment);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = "{\"error\":\"Fatal attempt of a breach of environments.\"}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
        return false;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           org.springframework.web.servlet.ModelAndView modelAndView) {
        response.setHeader(ENV_HEADER, environment);
    }
}
