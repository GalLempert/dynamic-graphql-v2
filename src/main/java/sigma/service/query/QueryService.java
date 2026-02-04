package sigma.service.query;

import sigma.dto.request.QueryRequest;
import sigma.dto.response.QueryResponse;
import sigma.model.Endpoint;
import sigma.persistence.repository.DynamicPostgresRepository;
import sigma.service.query.strategy.QueryExecutionStrategyFactory;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service layer for executing queries against PostgreSQL
 * Single Responsibility: Query execution and orchestration
 */
@Service
public class QueryService {

    private static final Logger logger = LoggerFactory.getLogger(QueryService.class);

    private final DynamicPostgresRepository postgresRepository;
    private final QueryBuilder queryBuilder;
    private final QueryExecutionStrategyFactory strategyFactory;

    public QueryService(DynamicPostgresRepository postgresRepository,
                        QueryBuilder queryBuilder,
                        QueryExecutionStrategyFactory strategyFactory) {
        this.postgresRepository = postgresRepository;
        this.queryBuilder = queryBuilder;
        this.strategyFactory = strategyFactory;
    }

    /**
     * Provides access to repository for polymorphic request execution
     * Used by QueryRequest implementations via Template Method pattern
     */
    public DynamicPostgresRepository getRepository() {
        return postgresRepository;
    }

    /**
     * Provides access to query builder for polymorphic request execution
     * Used by QueryRequest implementations via Template Method pattern
     */
    public QueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    /**
     * Executes a query request and returns a response
     * Uses polymorphism - ZERO switch statements!
     *
     * @param request The query request
     * @param endpoint The endpoint configuration
     * @return Query response
     */
    @Observed(name = "query.execution", contextualName = "query.execute")
    public QueryResponse execute(QueryRequest request, Endpoint endpoint) {
        logger.info("Executing {} query on endpoint: {}", request.getType(), endpoint != null ? endpoint.getName() : "<unknown>");
        return strategyFactory.getStrategy(endpoint).execute(request, endpoint, this);
    }
}
