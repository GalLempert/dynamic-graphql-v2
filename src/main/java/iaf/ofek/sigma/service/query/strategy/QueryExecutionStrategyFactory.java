package iaf.ofek.sigma.service.query.strategy;

import iaf.ofek.sigma.model.Endpoint;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Factory responsible for selecting the appropriate {@link QueryExecutionStrategy}.
 */
@Component
public class QueryExecutionStrategyFactory {

    private final List<QueryExecutionStrategy> strategies;

    public QueryExecutionStrategyFactory(List<QueryExecutionStrategy> strategies) {
        this.strategies = strategies.stream()
                // Deterministic order (nested first) when multiple strategies support an endpoint
                .sorted(Comparator.comparing(strategy -> strategy.getClass().getName()))
                .toList();
    }

    public QueryExecutionStrategy getStrategy(Endpoint endpoint) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(endpoint))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No query execution strategy found for endpoint"));
    }
}

