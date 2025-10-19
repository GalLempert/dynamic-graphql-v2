package iaf.ofek.sigma.service.write;

import com.mongodb.client.result.UpdateResult;
import iaf.ofek.sigma.dto.request.CreateRequest;
import iaf.ofek.sigma.dto.request.DeleteRequest;
import iaf.ofek.sigma.dto.request.UpdateRequest;
import iaf.ofek.sigma.dto.request.UpsertRequest;
import iaf.ofek.sigma.dto.request.WriteRequest;
import iaf.ofek.sigma.dto.response.CreateResponse;
import iaf.ofek.sigma.dto.response.DeleteResponse;
import iaf.ofek.sigma.dto.response.UpdateResponse;
import iaf.ofek.sigma.dto.response.UpsertResponse;
import iaf.ofek.sigma.dto.response.WriteResponse;
import iaf.ofek.sigma.filter.FilterTranslator;
import iaf.ofek.sigma.model.DynamicDocument;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.persistence.repository.DynamicMongoRepository;
import org.bson.BsonValue;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for executing write operations with ACID transaction support
 */
@Service
@Transactional
public class WriteService {

    private static final Logger logger = LoggerFactory.getLogger(WriteService.class);

    private final DynamicMongoRepository repository;
    private final FilterTranslator filterTranslator;

    public WriteService(DynamicMongoRepository repository, FilterTranslator filterTranslator) {
        this.repository = repository;
        this.filterTranslator = filterTranslator;
    }

    /**
     * Executes a write request using polymorphic dispatch
     */
    public WriteResponse execute(WriteRequest request, Endpoint endpoint) {
        logger.info("Executing {} operation on collection: {}", request.getType(), endpoint.getDatabaseCollection());
        return request.execute(this, endpoint);
    }

    public WriteResponse executeCreate(CreateRequest request, Endpoint endpoint) {
        SubEntityProcessor processor = new SubEntityProcessor(endpoint.getSubEntities());

        List<DynamicDocument> documents = request.getDocuments().stream()
                .map(doc -> prepareDocumentForInsert(doc, request.getRequestId(), processor))
                .collect(Collectors.toList());

        if (request.isBulk()) {
            List<String> insertedIds = repository.insertMany(endpoint.getDatabaseCollection(), documents);
            return new CreateResponse(insertedIds);
        }

        String insertedId = repository.insertOne(endpoint.getDatabaseCollection(), documents.get(0));
        return new CreateResponse(List.of(insertedId));
    }

    public WriteResponse executeUpdate(UpdateRequest request, Endpoint endpoint) {
        Query query = translateFilter(request.getFilter());
        Query existenceQuery = cloneQuery(query);

        Map<String, Object> updates = new HashMap<>(request.getUpdates());
        updates.remove("isDeleted");
        updates.put("latestRequestId", request.getRequestId());

        SubEntityProcessor processor = new SubEntityProcessor(endpoint.getSubEntities());
        if (processor.hasSubEntityPayload(updates)) {
            if (request.isUpdateMultiple()) {
                throw new IllegalArgumentException("Sub-entity operations only support single document updates");
            }

            Document existing = repository.findOneActive(endpoint.getDatabaseCollection(), cloneQuery(query));
            if (existing == null) {
                failForMissingOrDeleted(endpoint, existenceQuery, "Update");
            }
            processor.mergeForUpdate(updates, existing);
        }

        UpdateResult result = repository.update(
                endpoint.getDatabaseCollection(),
                query,
                updates,
                request.isUpdateMultiple()
        );

        if (result.getMatchedCount() == 0) {
            failForMissingOrDeleted(endpoint, existenceQuery, "Update");
        }

        return new UpdateResponse(result.getMatchedCount(), result.getModifiedCount());
    }

    public WriteResponse executeDelete(DeleteRequest request, Endpoint endpoint) {
        Query query = translateFilter(request.getFilter());
        Query existenceQuery = cloneQuery(query);

        UpdateResult result = repository.softDelete(
                endpoint.getDatabaseCollection(),
                query,
                request.isDeleteMultiple(),
                request.getRequestId()
        );

        if (result.getMatchedCount() == 0) {
            failForMissingOrDeleted(endpoint, existenceQuery, "Delete");
        }

        return new DeleteResponse(result.getModifiedCount());
    }

    public WriteResponse executeUpsert(UpsertRequest request, Endpoint endpoint) {
        Query query = translateFilter(request.getFilter());
        Query existenceQuery = cloneQuery(query);

        Map<String, Object> document = new HashMap<>(request.getDocument());
        document.remove("isDeleted");
        document.put("latestRequestId", request.getRequestId());

        SubEntityProcessor processor = new SubEntityProcessor(endpoint.getSubEntities());
        Document existing = repository.findOneActive(endpoint.getDatabaseCollection(), cloneQuery(query));

        if (existing != null) {
            if (processor.hasSubEntityPayload(document)) {
                processor.mergeForUpdate(document, existing);
            }

            UpdateResult result = repository.update(endpoint.getDatabaseCollection(), query, document, false);
            if (result.getMatchedCount() == 0) {
                failForMissingOrDeleted(endpoint, existenceQuery, "Upsert");
            }
            return new UpsertResponse(false, null, result.getMatchedCount(), result.getModifiedCount());
        }

        if (repository.existsIncludingDeleted(endpoint.getDatabaseCollection(), existenceQuery)) {
            throw new IllegalArgumentException("Upsert failed: target document is deleted");
        }

        processor.prepareForInsert(document);
        document.put("isDeleted", false);

        UpdateResult result = repository.upsert(endpoint.getDatabaseCollection(), query, document);

        BsonValue upsertedId = result.getUpsertedId();
        boolean wasInserted = upsertedId != null;
        String documentId = wasInserted ? upsertedId.asObjectId().getValue().toString() : null;

        return new UpsertResponse(
                wasInserted,
                documentId,
                result.getMatchedCount(),
                result.getModifiedCount()
        );
    }

    private DynamicDocument prepareDocumentForInsert(Map<String, Object> source,
                                                     String requestId,
                                                     SubEntityProcessor processor) {
        Map<String, Object> document = source != null ? new HashMap<>(source) : new HashMap<>();
        processor.prepareForInsert(document);

        DynamicDocument dynamicDocument = new DynamicDocument(document);
        dynamicDocument.setLatestRequestId(requestId);
        dynamicDocument.setDeleted(false);
        return dynamicDocument;
    }

    private Query translateFilter(Map<String, Object> filter) {
        iaf.ofek.sigma.model.filter.FilterRequest filterRequest =
                new iaf.ofek.sigma.model.filter.FilterRequest(filter, null);
        return filterTranslator.translate(filterRequest);
    }

    private Query cloneQuery(Query query) {
        return query == null ? new Query() : new BasicQuery(query.getQueryObject());
    }

    private void failForMissingOrDeleted(Endpoint endpoint, Query existenceQuery, String operation) {
        boolean exists = repository.existsIncludingDeleted(endpoint.getDatabaseCollection(), existenceQuery);
        if (exists) {
            throw new IllegalArgumentException(operation + " failed: target document is deleted");
        }
        throw new IllegalArgumentException(operation + " failed: no document matches the provided filter");
    }
}

