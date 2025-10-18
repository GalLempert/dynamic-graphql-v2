package iaf.ofek.sigma.persistence.repository;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import iaf.ofek.sigma.persistence.entity.SequenceCheckpoint;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.ChangeStreamOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dynamic MongoDB repository for querying and writing to arbitrary collections
 * Supports:
 * - Read operations (queries, change streams)
 * - Write operations (insert, update, delete, upsert)
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
        return mongoTemplate.count(new Query(), collectionName);
    }

    /**
     * Finds documents using a complex query with filters, sorting, pagination, and projection
     */
    public List<Document> findWithQuery(String collectionName, Query query) {
        logger.debug("Executing query on collection {}: {}", collectionName, query);
        return mongoTemplate.find(query, Document.class, collectionName);
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

    // ========== WRITE OPERATIONS ==========

    /**
     * Inserts a single document into the collection
     *
     * @param collectionName The collection name
     * @param document The document to insert
     * @return The ID of the inserted document
     */
    public String insertOne(String collectionName, Map<String, Object> document) {
        logger.info("Inserting document into collection: {}", collectionName);
        Document doc = new Document(document);
        InsertOneResult result = mongoTemplate.getCollection(collectionName).insertOne(doc);

        String insertedId = result.getInsertedId().asObjectId().getValue().toString();
        logger.info("Successfully inserted document with id: {}", insertedId);
        return insertedId;
    }

    /**
     * Inserts multiple documents into the collection (bulk insert)
     *
     * @param collectionName The collection name
     * @param documents The documents to insert
     * @return List of IDs of inserted documents
     */
    public List<String> insertMany(String collectionName, List<Map<String, Object>> documents) {
        logger.info("Inserting {} documents into collection: {}", documents.size(), collectionName);

        List<Document> docs = documents.stream()
                .map(Document::new)
                .collect(Collectors.toList());

        InsertManyResult result = mongoTemplate.getCollection(collectionName).insertMany(docs);

        List<String> insertedIds = result.getInsertedIds().values().stream()
                .map(bsonValue -> bsonValue.asObjectId().getValue().toString())
                .collect(Collectors.toList());

        logger.info("Successfully inserted {} documents", insertedIds.size());
        return insertedIds;
    }

    /**
     * Updates document(s) matching the query
     *
     * @param collectionName The collection name
     * @param query The query to find documents
     * @param updates The updates to apply
     * @param updateMultiple Whether to update all matching documents or just the first
     * @return UpdateResult containing matched and modified counts
     */
    public UpdateResult update(String collectionName, Query query, Map<String, Object> updates, boolean updateMultiple) {
        logger.info("Updating documents in collection: {} (multiple: {})", collectionName, updateMultiple);

        // Build Update object
        Update update = new Update();
        updates.forEach(update::set);

        UpdateResult result;
        if (updateMultiple) {
            result = mongoTemplate.updateMulti(query, update, collectionName);
        } else {
            result = mongoTemplate.updateFirst(query, update, collectionName);
        }

        logger.info("Update result: matched={}, modified={}", result.getMatchedCount(), result.getModifiedCount());
        return result;
    }

    /**
     * Upserts a document (update if exists, insert if not)
     *
     * @param collectionName The collection name
     * @param query The query to find the document
     * @param document The document data to upsert
     * @return UpdateResult containing matched/modified counts and upserted ID
     */
    public UpdateResult upsert(String collectionName, Query query, Map<String, Object> document) {
        logger.info("Upserting document in collection: {}", collectionName);

        // Build Update object
        Update update = new Update();
        document.forEach(update::set);

        UpdateResult result = mongoTemplate.upsert(query, update, collectionName);

        if (result.getUpsertedId() != null) {
            logger.info("Upserted new document with id: {}", result.getUpsertedId());
        } else {
            logger.info("Updated existing document: matched={}, modified={}",
                    result.getMatchedCount(), result.getModifiedCount());
        }

        return result;
    }

    /**
     * Deletes document(s) matching the query
     *
     * @param collectionName The collection name
     * @param query The query to find documents to delete
     * @param deleteMultiple Whether to delete all matching documents or just the first
     * @return DeleteResult containing the count of deleted documents
     */
    public DeleteResult delete(String collectionName, Query query, boolean deleteMultiple) {
        logger.info("Deleting documents from collection: {} (multiple: {})", collectionName, deleteMultiple);

        DeleteResult result;
        if (deleteMultiple) {
            result = mongoTemplate.remove(query, collectionName);
        } else {
            result = mongoTemplate.remove(query.limit(1), collectionName);
        }

        logger.info("Delete result: deleted={}", result.getDeletedCount());
        return result;
    }
}

