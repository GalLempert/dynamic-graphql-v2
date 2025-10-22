package sigma.dto.response;

import sigma.dto.request.WriteRequest;

import java.util.List;
import java.util.Map;

/**
 * Base interface for all write operation responses
 * 
 * Extends Response for unified polymorphic handling
 */
public interface WriteResponse extends Response {

    /**
     * Returns the type of write operation that was performed
     */
    WriteRequest.WriteType getType();

    /**
     * Returns true if the operation was successful
     */
    boolean isSuccess();

    /**
     * Returns the number of documents affected
     */
    long getAffectedCount();

    /**
     * Returns the documents impacted by the write operation in their most
     * recent state.
     */
    List<Map<String, Object>> getDocuments();

    /**
     * Human readable explanation describing the outcome of the operation.
     */
    String getMessage();

    /**
     * Visitor pattern for building HTTP responses
     * Allows polymorphic dispatch without instanceof checks
     */
    <T> T accept(ResponseVisitor<T> visitor);
}
