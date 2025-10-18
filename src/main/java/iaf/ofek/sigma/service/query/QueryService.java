package iaf.ofek.sigma.service.query;

import iaf.ofek.sigma.dto.request.QueryRequest;
import iaf.ofek.sigma.dto.response.QueryResponse;
import iaf.ofek.sigma.persistence.repository.DynamicMongoRepository;
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

    public QueryService(DynamicMongoRepository mongoRepository, QueryBuilder queryBuilder) {
        this.mongoRepository = mongoRepository;
        this.queryBuilder = queryBuilder;
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
    public QueryResponse execute(QueryRequest request, String collectionName) {
        logger.info("Executing {} query on collection: {}", request.getType(), collectionName);
        return request.execute(this, collectionName);
    }
}
