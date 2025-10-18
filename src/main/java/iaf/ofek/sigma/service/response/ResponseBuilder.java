package iaf.ofek.sigma.service.response;

import iaf.ofek.sigma.dto.response.DocumentListResponse;
import iaf.ofek.sigma.dto.response.ErrorResponse;
import iaf.ofek.sigma.dto.response.QueryResponse;
import iaf.ofek.sigma.dto.response.SequenceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds HTTP ResponseEntity objects from QueryResponse objects
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
}
