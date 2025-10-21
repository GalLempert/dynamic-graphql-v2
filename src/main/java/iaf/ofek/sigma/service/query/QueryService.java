package iaf.ofek.sigma.service.query;

import iaf.ofek.sigma.dto.request.QueryRequest;
import iaf.ofek.sigma.dto.response.QueryResponse;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.persistence.repository.DynamicMongoRepository;
import iaf.ofek.sigma.service.query.strategy.QueryExecutionStrategyFactory;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service layer for executing queries against MongoDB
 * Single Responsibility: Query execution and orchestration
 *
 * Renamed from GraphQLEngine to better reflect its purpose
 */
@Service
public class QueryService {

    private static final Logger logger = LoggerFactory.getLogger(QueryService.class);

    private final DynamicMongoRepository mongoRepository;
    private final QueryBuilder queryBuilder;
    private final QueryExecutionStrategyFactory strategyFactory;

    public QueryService(DynamicMongoRepository mongoRepository,
                        QueryBuilder queryBuilder,
                        QueryExecutionStrategyFactory strategyFactory) {
        this.mongoRepository = mongoRepository;
        this.queryBuilder = queryBuilder;
        this.strategyFactory = strategyFactory;
    }

    /**
     * Provides access to repository for polymorphic request execution
     * Used by QueryRequest implementations via Template Method pattern
     */
    public DynamicMongoRepository getRepository() {
        return mongoRepository;
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
     * @param collectionName The collection to query
     * @return Query response
     */
    @Observed(name = "query.execution", contextualName = "query.execute")
    public QueryResponse execute(QueryRequest request, Endpoint endpoint) {
        logger.info("Executing {} query on endpoint: {}", request.getType(), endpoint != null ? endpoint.getName() : "<unknown>");
        return strategyFactory.getStrategy(endpoint).execute(request, endpoint, this);
    }
}
