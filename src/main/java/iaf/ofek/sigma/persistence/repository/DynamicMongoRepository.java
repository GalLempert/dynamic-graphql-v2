package iaf.ofek.sigma.persistence.repository;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import iaf.ofek.sigma.persistence.entity.SequenceCheckpoint;
import org.bson.BsonDocument;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.ChangeStreamOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamic MongoDB repository for querying arbitrary collections
 * Supports both regular queries and Change Stream queries
 */
@Repository
public class DynamicMongoRepository {

    private static final Logger logger = LoggerFactory.getLogger(DynamicMongoRepository.class);

    private final MongoTemplate mongoTemplate;
    private final SequenceCheckpointRepository checkpointRepository;

    public DynamicMongoRepository(MongoTemplate mongoTemplate, SequenceCheckpointRepository checkpointRepository) {
        this.mongoTemplate = mongoTemplate;
        this.checkpointRepository = checkpointRepository;
    }

    /**
     * Performs a select * query on the specified collection
     */
    public List<Document> findAll(String collectionName) {
        logger.debug("Querying all documents from collection: {}", collectionName);
        return mongoTemplate.findAll(Document.class, collectionName);
    }

    /**
     * Finds a document by ID
     */
    public Document findById(String collectionName, String id) {
        logger.debug("Querying document with id {} from collection: {}", id, collectionName);
        return mongoTemplate.findById(id, Document.class, collectionName);
    }

    /**
     * Counts documents in a collection
     */
    public long count(String collectionName) {
        logger.debug("Counting documents in collection: {}", collectionName);
        return mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(), collectionName);
    }

    /**
     * Gets the next page of changes using Change Streams with sequence-based pagination
     */
    public Map<String, Object> getNextPageBySequence(String collectionName, long startSequence, int batchSize) {
        logger.info("Getting next page for collection: {}, startSequence: {}, batchSize: {}",
                   collectionName, startSequence, batchSize);

        // 1. Retrieve the Resume Token based on the startSequence
        BsonDocument token = loadResumeTokenForSequence(collectionName, startSequence);

        // 2. Open the Change Stream
        var collection = mongoTemplate.getCollection(collectionName);
        var stream = collection.watch();

        // 3. Conditionally set the resume point
        if (token != null) {
            stream = stream.resumeAfter(token);
            logger.debug("Resuming from token for sequence: {}", startSequence);
        } else {
            logger.debug("Starting new change stream (no checkpoint found)");
        }

        // 4. Collect and process the events
        return processAndCheckpointChanges(collectionName, stream, batchSize, startSequence);
    }

    /**
     * Loads the resume token for a given sequence number
     */
    private BsonDocument loadResumeTokenForSequence(String collectionName, long startSequence) {
        return checkpointRepository.findByCollectionName(collectionName)
                .filter(checkpoint -> checkpoint.getSequence() == startSequence)
                .map(checkpoint -> {
                    String tokenJson = checkpoint.getResumeToken();
                    if (tokenJson != null && !tokenJson.isEmpty()) {
                        return BsonDocument.parse(tokenJson);
                    }
                    return null;
                })
                .orElse(null);
    }

    /**
     * Processes change stream events and checkpoints progress
     */
    private Map<String, Object> processAndCheckpointChanges(
            String collectionName,
            com.mongodb.client.ChangeStreamIterable<Document> stream,
            int batchSize,
            long startSequence) {

        List<Map<String, Object>> results = new ArrayList<>();
        BsonDocument lastToken = null;
        long currentSequence = startSequence;

        try (MongoCursor<ChangeStreamDocument<Document>> cursor = stream.iterator()) {
            for (int i = 0; i < batchSize && cursor.hasNext(); i++) {
                ChangeStreamDocument<Document> change = cursor.next();

                // 1. Process and Collect Result
                Map<String, Object> documentData = extractData(change);
                results.add(documentData);

                // 2. Track Token and Sequence
                lastToken = change.getResumeToken();
                currentSequence++; // Increment sequence
            }
        } catch (Exception e) {
            logger.error("Error processing change stream for collection: {}", collectionName, e);
        }

        // 3. Save the new checkpoint if we processed any changes
        if (lastToken != null) {
            saveNewCheckpoint(collectionName, lastToken, currentSequence);
        }

        // 4. Build response
        Map<String, Object> response = new HashMap<>();
        response.put("data", results);
        response.put("nextSequence", currentSequence);
        response.put("hasMore", !results.isEmpty() && results.size() == batchSize);

        return response;
    }

    /**
     * Extracts data from a change stream document
     */
    private Map<String, Object> extractData(ChangeStreamDocument<Document> change) {
        Map<String, Object> result = new HashMap<>();
        result.put("operationType", change.getOperationType().getValue());
        result.put("documentKey", change.getDocumentKey());

        Document fullDocument = change.getFullDocument();
        if (fullDocument != null) {
            result.put("fullDocument", fullDocument);
        }

        return result;
    }

    /**
     * Saves a new checkpoint for the collection
     */
    private void saveNewCheckpoint(String collectionName, BsonDocument token, long sequence) {
        String tokenJson = token.toJson();
        SequenceCheckpoint checkpoint = new SequenceCheckpoint(collectionName, sequence, tokenJson);
        checkpointRepository.save(checkpoint);
        logger.debug("Saved checkpoint for collection: {}, sequence: {}", collectionName, sequence);
    }
}

