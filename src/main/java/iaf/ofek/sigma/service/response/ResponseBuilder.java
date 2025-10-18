package iaf.ofek.sigma.service.response;

import iaf.ofek.sigma.dto.response.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds HTTP ResponseEntity objects from QueryResponse and WriteResponse objects
 * Single Responsibility: Response formatting
 * 
 * Uses Visitor pattern to eliminate instanceof checks - pure OOP polymorphism
 */
@Service
public class ResponseBuilder implements ResponseVisitor<ResponseEntity<?>> {

    /**
     * Builds an HTTP response from a QueryResponse
     * Uses Visitor pattern - no instanceof checks
     *
     * @param queryResponse The query response
     * @return HTTP ResponseEntity
     */
    public ResponseEntity<?> build(QueryResponse queryResponse) {
        return queryResponse.accept(this);
    }

    // ========== VISITOR PATTERN IMPLEMENTATIONS ==========
    
    @Override
    public ResponseEntity<?> visitDocumentList(DocumentListResponse response) {
        return ResponseEntity.ok(response.getDocuments());
    }

    @Override
    public ResponseEntity<?> visitSequence(SequenceResponse response) {
        Map<String, Object> body = new HashMap<>();
        body.put("nextSequence", response.getNextSequence());
        body.put("data", response.getData());
        body.put("hasMore", response.isHasMore());

        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<?> visitError(ErrorResponse response) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", response.getErrorMessage());

        if (response.getDetails() != null && !response.getDetails().isEmpty()) {
            body.put("details", response.getDetails());
        }

        return ResponseEntity.badRequest().body(body);
    }

    @Override
    public ResponseEntity<?> visitCreate(CreateResponse response) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "CREATE");
        body.put("success", response.isSuccess());
        body.put("affectedCount", response.getAffectedCount());
        body.put("insertedIds", response.getInsertedIds());
        body.put("insertedCount", response.getInsertedCount());

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Override
    public ResponseEntity<?> visitUpdate(UpdateResponse response) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "UPDATE");
        body.put("success", response.isSuccess());
        body.put("affectedCount", response.getAffectedCount());
        body.put("matchedCount", response.getMatchedCount());
        body.put("modifiedCount", response.getModifiedCount());

        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<?> visitDelete(DeleteResponse response) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "DELETE");
        body.put("success", response.isSuccess());
        body.put("affectedCount", response.getAffectedCount());
        body.put("deletedCount", response.getDeletedCount());

        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<?> visitUpsert(UpsertResponse response) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "UPSERT");
        body.put("success", response.isSuccess());
        body.put("affectedCount", response.getAffectedCount());
        body.put("wasInserted", response.isWasInserted());

        if (response.isWasInserted()) {
            body.put("documentId", response.getDocumentId());
        } else {
            body.put("matchedCount", response.getMatchedCount());
            body.put("modifiedCount", response.getModifiedCount());
        }

        HttpStatus status = response.isWasInserted() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(body);
    }

    // ========== UTILITY METHODS ==========

    /**
     * Builds a validation error response
     */
    public ResponseEntity<?> buildValidationError(String message, java.util.List<String> details) {
        ErrorResponse errorResponse = new ErrorResponse(message, details);
        return visitError(errorResponse);
    }

    /**
     * Builds a generic error response
     */
    public ResponseEntity<?> buildError(String message) {
        ErrorResponse errorResponse = new ErrorResponse(message);
        return visitError(errorResponse);
    }

    // ========== WRITE RESPONSE METHODS ==========

    /**
     * Builds an HTTP response from a write response (or error)
     * Uses Visitor pattern - ZERO instanceof checks!
     *
     * @param response The response (WriteResponse or ErrorResponse - both implement Response)
     * @return HTTP ResponseEntity
     */
    public ResponseEntity<?> buildWrite(iaf.ofek.sigma.dto.response.Response response) {
        return response.accept(this);
    }
}
