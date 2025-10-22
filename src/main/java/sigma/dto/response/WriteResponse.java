package sigma.dto.response;

import sigma.dto.request.WriteRequest;

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
     * Visitor pattern for building HTTP responses
     * Allows polymorphic dispatch without instanceof checks
     */
    <T> T accept(ResponseVisitor<T> visitor);
}
