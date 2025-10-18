package iaf.ofek.sigma.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Implementation of Spring Data's AuditorAware interface
 * Provides the current auditor (user/system) for @CreatedBy and @LastModifiedBy fields
 *
 * Current implementation returns system identifier from environment
 * Can be extended to return authenticated user from security context
 */
@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        // Get service name from environment variable
        // In production, this could be replaced with:
        // - SecurityContextHolder.getContext().getAuthentication().getName()
        // - JWT token claims
        // - Request headers (X-User-Id, etc.)
        String serviceName = System.getenv("SERVICE");
        return Optional.ofNullable(serviceName != null ? serviceName : "SYSTEM");
    }
}
