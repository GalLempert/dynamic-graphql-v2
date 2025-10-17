package iaf.ofek.sigma.engine;

import iaf.ofek.sigma.persistence.repository.DynamicMongoRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic engine
 * Handles data processing and orchestrates repository calls
 * Converts sequence-based pagination to client-friendly format
 */
@Service
public class GraphQLEngine {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLEngine.class);

    private final DynamicMongoRepository mongoRepository;

    public GraphQLEngine(DynamicMongoRepository mongoRepository) {
        this.mongoRepository = mongoRepository;
    }

    /**
     * Queries all documents from a collection
     */
    public List<Document> queryCollection(String collectionName) {
        logger.info("Querying collection: {}", collectionName);
        return mongoRepository.findAll(collectionName);
    }

    /**
     * Queries a single document by ID
     */
    public Document queryDocumentById(String collectionName, String id) {
        logger.info("Querying document {} from collection: {}", id, collectionName);
        return mongoRepository.findById(collectionName, id);
    }

    /**
     * Gets count of documents in a collection
     */
    public long getCollectionCount(String collectionName) {
        logger.info("Getting count for collection: {}", collectionName);
        return mongoRepository.count(collectionName);
    }

    /**
     * Queries changes using sequence-based pagination with Change Streams
     * Returns a client-friendly response with nextSequence and data
     */
    public Map<String, Object> queryBySequence(String collectionName, long startSequence, int bulkSize) {
        logger.info("Querying by sequence for collection: {}, sequence: {}, bulkSize: {}",
                   collectionName, startSequence, bulkSize);

        // Get changes from repository
        Map<String, Object> repositoryResponse = mongoRepository.getNextPageBySequence(
            collectionName,
            startSequence,
            bulkSize
        );

        // Transform to client-friendly format
        Map<String, Object> clientResponse = new HashMap<>();
        clientResponse.put("nextSequence", repositoryResponse.get("nextSequence"));
        clientResponse.put("data", repositoryResponse.get("data"));
        clientResponse.put("hasMore", repositoryResponse.get("hasMore"));

        return clientResponse;
    }
}
