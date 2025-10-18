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
 */
@Service
public class ResponseBuilder {

    /**
     * Builds an HTTP response from a QueryResponse
     *
     * @param queryResponse The query response
     * @return HTTP ResponseEntity
     */
    public ResponseEntity<?> build(QueryResponse queryResponse) {
        if (!queryResponse.isSuccess()) {
            return buildErrorResponse((ErrorResponse) queryResponse);
        }

        if (queryResponse instanceof DocumentListResponse docResponse) {
            return buildDocumentListResponse(docResponse);
        }

        if (queryResponse instanceof SequenceResponse seqResponse) {
            return buildSequenceResponse(seqResponse);
        }

        // Unknown response type
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Unknown response type");
    }

    /**
     * Builds response for document list
     */
    private ResponseEntity<?> buildDocumentListResponse(DocumentListResponse response) {
        return ResponseEntity.ok(response.getDocuments());
    }

    /**
     * Builds response for sequence query
     */
    private ResponseEntity<?> buildSequenceResponse(SequenceResponse response) {
        Map<String, Object> body = new HashMap<>();
        body.put("nextSequence", response.getNextSequence());
        body.put("data", response.getData());
        body.put("hasMore", response.isHasMore());

        return ResponseEntity.ok(body);
    }

    /**
     * Builds error response
     */
    private ResponseEntity<?> buildErrorResponse(ErrorResponse response) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", response.getErrorMessage());

        if (response.getDetails() != null && !response.getDetails().isEmpty()) {
            body.put("details", response.getDetails());
        }

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Builds a validation error response
     */
    public ResponseEntity<?> buildValidationError(String message, java.util.List<String> details) {
        ErrorResponse errorResponse = new ErrorResponse(message, details);
        return buildErrorResponse(errorResponse);
    }

    /**
     * Builds a generic error response
     */
    public ResponseEntity<?> buildError(String message) {
        ErrorResponse errorResponse = new ErrorResponse(message);
        return buildErrorResponse(errorResponse);
    }

    // ========== WRITE RESPONSE METHODS ==========

    /**
     * Builds an HTTP response from a write response (or error)
     *
     * @param response The write response or error response
     * @return HTTP ResponseEntity
     */
    public ResponseEntity<?> buildWrite(Object response) {
        // Handle error responses
        if (response instanceof ErrorResponse errorResponse) {
            return buildErrorResponse(errorResponse);
        }

        // Handle write responses
        if (response instanceof WriteResponse writeResponse) {
            return buildWriteResponse(writeResponse);
        }

        // Unknown response type
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Unknown response type");
    }

    /**
     * Builds response for write operations
     */
    private ResponseEntity<?> buildWriteResponse(WriteResponse response) {
        if (response instanceof CreateResponse createResponse) {
            return buildCreateResponse(createResponse);
        }

        if (response instanceof UpdateResponse updateResponse) {
            return buildUpdateResponse(updateResponse);
        }

        if (response instanceof DeleteResponse deleteResponse) {
            return buildDeleteResponse(deleteResponse);
        }

        if (response instanceof UpsertResponse upsertResponse) {
            return buildUpsertResponse(upsertResponse);
        }

        // Unknown write response type
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Unknown write response type");
    }

    /**
     * Builds response for CREATE operation
     */
    private ResponseEntity<?> buildCreateResponse(CreateResponse response) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "CREATE");
        body.put("success", response.isSuccess());
        body.put("affectedCount", response.getAffectedCount());
        body.put("insertedIds", response.getInsertedIds());
        body.put("insertedCount", response.getInsertedCount());

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /**
     * Builds response for UPDATE operation
     */
    private ResponseEntity<?> buildUpdateResponse(UpdateResponse response) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "UPDATE");
        body.put("success", response.isSuccess());
        body.put("affectedCount", response.getAffectedCount());
        body.put("matchedCount", response.getMatchedCount());
        body.put("modifiedCount", response.getModifiedCount());

        return ResponseEntity.ok(body);
    }

    /**
     * Builds response for DELETE operation
     */
    private ResponseEntity<?> buildDeleteResponse(DeleteResponse response) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "DELETE");
        body.put("success", response.isSuccess());
        body.put("affectedCount", response.getAffectedCount());
        body.put("deletedCount", response.getDeletedCount());

        return ResponseEntity.ok(body);
    }

    /**
     * Builds response for UPSERT operation
     */
    private ResponseEntity<?> buildUpsertResponse(UpsertResponse response) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "UPSERT");
        body.put("success", response.isSuccess());
        body.put("affectedCount", response.getAffectedCount());
        body.put("wasInserted", response.wasInserted());

        if (response.wasInserted()) {
            body.put("documentId", response.getDocumentId());
        } else {
            body.put("matchedCount", response.getMatchedCount());
            body.put("modifiedCount", response.getModifiedCount());
        }

        HttpStatus status = response.wasInserted() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(body);
    }
}
