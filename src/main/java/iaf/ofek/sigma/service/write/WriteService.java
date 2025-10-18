package iaf.ofek.sigma.service.write;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import iaf.ofek.sigma.dto.request.*;
import iaf.ofek.sigma.dto.response.*;
import iaf.ofek.sigma.filter.FilterTranslator;
import iaf.ofek.sigma.model.AuditFields;
import iaf.ofek.sigma.persistence.repository.DynamicMongoRepository;
import org.bson.BsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for executing write operations
 *
 * Responsibilities:
 * - Execute CREATE, UPDATE, DELETE, UPSERT operations
 * - Inject audit fields automatically
 * - Translate filters to MongoDB queries
 * - Call repository layer
 */
@Service
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
     */
    public WriteResponse executeCreate(CreateRequest request, String collectionName) {
        List<Map<String, Object>> documents = request.getDocuments().stream()
                .map(doc -> AuditFields.injectForCreate(doc, request.getRequestId()))
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
     */
    public WriteResponse executeUpdate(UpdateRequest request, String collectionName) {
        // Translate filter to MongoDB query
        iaf.ofek.sigma.model.filter.FilterRequest filterRequest =
                new iaf.ofek.sigma.model.filter.FilterRequest(request.getFilter(), null);
        Query query = filterTranslator.translate(filterRequest);

        // Inject audit fields into updates
        Map<String, Object> updates = AuditFields.injectForUpdate(
                request.getUpdates(),
                request.getRequestId()
        );

        // Execute update
        UpdateResult result = repository.update(
                collectionName,
                query,
                updates,
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
     */
    public WriteResponse executeUpsert(UpsertRequest request, String collectionName) {
        // Translate filter to MongoDB query
        iaf.ofek.sigma.model.filter.FilterRequest filterRequest =
                new iaf.ofek.sigma.model.filter.FilterRequest(request.getFilter(), null);
        Query query = filterTranslator.translate(filterRequest);

        // Inject audit fields (use injectForCreate as it may be a new document)
        Map<String, Object> document = AuditFields.injectForCreate(
                request.getDocument(),
                request.getRequestId()
        );

        // Execute upsert
        UpdateResult result = repository.upsert(collectionName, query, document);

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
