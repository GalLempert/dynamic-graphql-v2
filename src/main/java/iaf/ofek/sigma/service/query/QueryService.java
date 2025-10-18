package iaf.ofek.sigma.service.query;

import iaf.ofek.sigma.dto.request.FilteredQueryRequest;
import iaf.ofek.sigma.dto.request.FullCollectionRequest;
import iaf.ofek.sigma.dto.request.QueryRequest;
import iaf.ofek.sigma.dto.request.SequenceQueryRequest;
import iaf.ofek.sigma.dto.response.DocumentListResponse;
import iaf.ofek.sigma.dto.response.QueryResponse;
import iaf.ofek.sigma.dto.response.SequenceResponse;
import iaf.ofek.sigma.persistence.repository.DynamicMongoRepository;
import io.micrometer.observation.annotation.Observed;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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

    public DynamicMongoRepository getRepository() {
        return mongoRepository;
    }

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

    /**
     * Executes a full collection query
     */
    private QueryResponse executeFullCollection(String collectionName) {
        List<Document> documents = mongoRepository.findAll(collectionName);
        logger.debug("Full collection query returned {} documents", documents.size());
        return new DocumentListResponse(documents);
    }

    /**
     * Executes a filtered query
     */
    private QueryResponse executeFiltered(FilteredQueryRequest request, String collectionName) {
        Query query = queryBuilder.build(request);
        List<Document> documents = mongoRepository.findWithQuery(collectionName, query);
        logger.debug("Filtered query returned {} documents", documents.size());
        return new DocumentListResponse(documents);
    }

    /**
     * Executes a sequence-based query
     */
    private QueryResponse executeSequence(SequenceQueryRequest request, String collectionName) {
        Map<String, Object> result = mongoRepository.getNextPageBySequence(
                collectionName,
                request.getStartSequence(),
                request.getBulkSize()
        );

        long nextSequence = (Long) result.get("nextSequence");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        boolean hasMore = (Boolean) result.get("hasMore");

        logger.debug("Sequence query returned {} items, nextSequence={}, hasMore={}",
                data.size(), nextSequence, hasMore);

        return new SequenceResponse(nextSequence, data, hasMore);
    }
}
