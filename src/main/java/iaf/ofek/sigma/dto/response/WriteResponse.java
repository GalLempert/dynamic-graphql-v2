package iaf.ofek.sigma.dto.response;

import iaf.ofek.sigma.dto.request.WriteRequest;

/**
 * Base interface for all write operation responses
 */
public interface WriteResponse {

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
}
