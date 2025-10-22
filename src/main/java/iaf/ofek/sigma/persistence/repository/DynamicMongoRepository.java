package iaf.ofek.sigma.persistence.repository;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import iaf.ofek.sigma.model.DynamicDocument;
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
     * Used by FullCollectionRequest
     */
    public List<Document> findAll(String collectionName) {
        logger.debug("Querying all documents from collection: {}", collectionName);
        Query query = applyNotDeletedFilter(new Query());
        return mongoTemplate.find(query, Document.class, collectionName);
    }

    /**
     * Finds documents using a complex query with filters, sorting, pagination, and projection
     * Used by FilteredQueryRequest
     */
    public List<Document> findWithQuery(String collectionName, Query query) {
        logger.debug("Executing query on collection {}: {}", collectionName, query);
        Query safeQuery = applyNotDeletedFilter(query);
        return mongoTemplate.find(safeQuery, Document.class, collectionName);
    }

    /**
     * Finds nested documents by treating the specified field as a collection on its own.
     *
     * <p>The implementation uses an aggregation pipeline:</p>
     * <ol>
     *     <li>Filters out logically deleted parent documents</li>
     *     <li>Unwinds the nested field</li>
     *     <li>Replaces the root with the nested document content</li>
     *     <li>Applies filters, projections, pagination and sorting defined by the query</li>
     * </ol>
     *
     * @param collectionName The collection hosting the nested documents
     * @param query The query describing the desired filtering/sorting options
     * @param fatherDocumentPath Path of the nested document array inside the parent document
     * @return List of nested documents matching the query
     */
    public List<Document> findNestedDocuments(String collectionName, Query query, String fatherDocumentPath) {
        logger.debug("Executing nested query on collection {} -> field {} with query {}", collectionName, fatherDocumentPath, query);

        List<Document> pipeline = new ArrayList<>();

        // Stage 1: filter out deleted parent documents
        pipeline.add(new Document("$match", new Document("isDeleted", new Document("$ne", true))));

        // Stage 2: unwind nested array
        pipeline.add(new Document("$unwind", "$" + fatherDocumentPath));

        // Stage 3: promote nested document as the root document
        pipeline.add(new Document("$replaceRoot", new Document("newRoot", "$" + fatherDocumentPath)));

        Query safeQuery = applyNotDeletedFilter(query);

        Document matchStage = safeQuery.getQueryObject();
        if (matchStage != null && !matchStage.isEmpty()) {
            pipeline.add(new Document("$match", matchStage));
        }

        Document projection = safeQuery.getFieldsObject();
        if (projection != null && !projection.isEmpty()) {
            pipeline.add(new Document("$project", projection));
        }

        Document sort = safeQuery.getSortObject();
        if (sort != null && !sort.isEmpty()) {
            pipeline.add(new Document("$sort", sort));
        }

        if (safeQuery.getSkip() > 0) {
            pipeline.add(new Document("$skip", safeQuery.getSkip()));
        }

        if (safeQuery.getLimit() > 0) {
            pipeline.add(new Document("$limit", safeQuery.getLimit()));
        }

        return mongoTemplate.getCollection(collectionName)
                .aggregate(pipeline)
                .into(new ArrayList<>());
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
     * Uses DynamicDocument for automatic audit field management
     *
     * @param collectionName The collection name
     * @param document The DynamicDocument to insert
     * @return The ID of the inserted document
     */
    public String insertOne(String collectionName, DynamicDocument document) {
        logger.info("Inserting document into collection: {}", collectionName);

        // Save using MongoTemplate - audit fields automatically populated by Spring Data
        DynamicDocument saved = mongoTemplate.save(document, collectionName);

        String insertedId = saved.getId();
        logger.info("Successfully inserted document with id: {} (version: {})", insertedId, saved.getVersion());
        return insertedId;
    }

    /**
     * Inserts multiple documents into the collection (bulk insert)
     * Uses DynamicDocument for automatic audit field management
     *
     * @param collectionName The collection name
     * @param documents The DynamicDocuments to insert
     * @return List of IDs of inserted documents
     */
    public List<String> insertMany(String collectionName, List<DynamicDocument> documents) {
        logger.info("Inserting {} documents into collection: {}", documents.size(), collectionName);

        // Insert using MongoTemplate - audit fields automatically populated
        List<DynamicDocument> savedDocs = (List<DynamicDocument>) mongoTemplate.insert(documents, collectionName);

        List<String> insertedIds = savedDocs.stream()
                .map(DynamicDocument::getId)
                .collect(Collectors.toList());

        logger.info("Successfully inserted {} documents with audit fields", insertedIds.size());
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

        Query safeQuery = applyNotDeletedFilter(query);

        UpdateResult result;
        if (updateMultiple) {
            result = mongoTemplate.updateMulti(safeQuery, update, collectionName);
        } else {
            result = mongoTemplate.updateFirst(safeQuery, update, collectionName);
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

        Query safeQuery = applyNotDeletedFilter(query);

        UpdateResult result = mongoTemplate.upsert(safeQuery, update, collectionName);

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
    public DeleteResult delete(String collectionName, Query query, boolean deleteMultiple, String requestId) {
        logger.info("Deleting documents from collection: {} (multiple: {})", collectionName, deleteMultiple);

        Query safeQuery = applyNotDeletedFilter(query);

        Update update = new Update()
                .set("isDeleted", true)
                .currentDate("lastModifiedAt");

        if (requestId != null) {
            update.set("latestRequestId", requestId);
        }

        UpdateResult result;
        if (deleteMultiple) {
            result = mongoTemplate.updateMulti(safeQuery, update, collectionName);
        } else {
            result = mongoTemplate.updateFirst(safeQuery, update, collectionName);
        }

        logger.info("Soft delete result: matched={}, modified={}", result.getMatchedCount(), result.getModifiedCount());
        return DeleteResult.acknowledged(result.getModifiedCount());
    }

    /**
     * Ensures queries automatically ignore logically deleted documents
     */
    private Query applyNotDeletedFilter(Query query) {
        Query effectiveQuery = query != null ? query : new Query();
        effectiveQuery.getQueryObject().remove("isDeleted");
        effectiveQuery.addCriteria(Criteria.where("isDeleted").ne(true));
        return effectiveQuery;
    }
}

