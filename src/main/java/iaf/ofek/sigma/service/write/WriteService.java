package iaf.ofek.sigma.service.write;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import iaf.ofek.sigma.dto.request.*;
import iaf.ofek.sigma.dto.response.*;
import iaf.ofek.sigma.filter.FilterTranslator;
import iaf.ofek.sigma.model.DynamicDocument;
import iaf.ofek.sigma.persistence.repository.DynamicMongoRepository;
import org.bson.BsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for executing write operations with ACID transaction support
 *
 * Responsibilities:
 * - Execute CREATE, UPDATE, DELETE, UPSERT operations
 * - Inject audit fields automatically
 * - Translate filters to MongoDB queries
 * - Call repository layer
 * - Ensure ACID properties via @Transactional
 *
 * All write methods are transactional - if any operation fails, the entire
 * transaction will be rolled back to maintain data consistency.
 *
 * Note: MongoDB transactions require MongoDB 4.0+ with replica set configuration
 */
@Service
@Transactional  // All methods in this service run within a transaction by default
public class WriteService {

    private static final Logger logger = LoggerFactory.getLogger(WriteService.class);

    private final DynamicMongoRepository repository;
    private final FilterTranslator filterTranslator;

    public WriteService(DynamicMongoRepository repository, FilterTranslator filterTranslator) {
        this.repository = repository;
        this.filterTranslator = filterTranslator;
    }

    /**
     * Executes a write request
     * Uses polymorphism - ZERO switch statements!
     */
    public WriteResponse execute(WriteRequest request, String collectionName) {
        logger.info("Executing {} operation on collection: {}", request.getType(), collectionName);
        return request.execute(this, collectionName);
    }

    /**
     * Executes CREATE operation
     * Made public for Template Method pattern
     * Audit fields are automatically populated by Spring Data
     */
    public WriteResponse executeCreate(CreateRequest request, String collectionName) {
        // Convert Map to DynamicDocument - Spring Data will auto-populate audit fields
        List<DynamicDocument> documents = request.getDocuments().stream()
                .map(DynamicDocument::new)
                .collect(Collectors.toList());

        if (request.isBulk()) {
            List<String> insertedIds = repository.insertMany(collectionName, documents);
            return new CreateResponse(insertedIds);
        } else {
            String insertedId = repository.insertOne(collectionName, documents.get(0));
            return new CreateResponse(List.of(insertedId));
        }
    }

    /**
     * Executes UPDATE operation
     * Made public for Template Method pattern
     * lastModifiedAt and lastModifiedBy are automatically updated by Spring Data
     */
    public WriteResponse executeUpdate(UpdateRequest request, String collectionName) {
        // Translate filter to MongoDB query
        iaf.ofek.sigma.model.filter.FilterRequest filterRequest =
                new iaf.ofek.sigma.model.filter.FilterRequest(request.getFilter(), null);
        Query query = filterTranslator.translate(filterRequest);

        // Execute update - Spring Data will auto-update lastModifiedAt/lastModifiedBy
        UpdateResult result = repository.update(
                collectionName,
                query,
                request.getUpdates(),
                request.isUpdateMultiple()
        );

        return new UpdateResponse(result.getMatchedCount(), result.getModifiedCount());
    }

    /**
     * Executes DELETE operation
     * Made public for Template Method pattern
     */
    public WriteResponse executeDelete(DeleteRequest request, String collectionName) {
        // Translate filter to MongoDB query
        iaf.ofek.sigma.model.filter.FilterRequest filterRequest =
                new iaf.ofek.sigma.model.filter.FilterRequest(request.getFilter(), null);
        Query query = filterTranslator.translate(filterRequest);

        // Execute delete
        DeleteResult result = repository.delete(
                collectionName,
                query,
                request.isDeleteMultiple()
        );

        return new DeleteResponse(result.getDeletedCount());
    }

    /**
     * Executes UPSERT operation
     * Made public for Template Method pattern
     * Audit fields automatically managed by Spring Data (createdAt if insert, lastModifiedAt if update)
     */
    public WriteResponse executeUpsert(UpsertRequest request, String collectionName) {
        // Translate filter to MongoDB query
        iaf.ofek.sigma.model.filter.FilterRequest filterRequest =
                new iaf.ofek.sigma.model.filter.FilterRequest(request.getFilter(), null);
        Query query = filterTranslator.translate(filterRequest);

        // Execute upsert - Spring Data handles audit fields
        UpdateResult result = repository.upsert(collectionName, query, request.getDocument());

        boolean wasInserted = result.getUpsertedId() != null;
        String documentId = wasInserted
                ? result.getUpsertedId().asObjectId().getValue().toString()
                : null;

        return new UpsertResponse(
                wasInserted,
                documentId,
                result.getMatchedCount(),
                result.getModifiedCount()
        );
    }
}
