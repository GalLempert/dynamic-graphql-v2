package iaf.ofek.sigma.dto.response;

import iaf.ofek.sigma.dto.request.WriteRequest;
import org.bson.Document;

import java.util.List;

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
     * Returns the latest state of the document(s) touched by the write operation.
     */
    List<Document> getDocuments();

    /**
     * Visitor pattern for building HTTP responses
     * Allows polymorphic dispatch without instanceof checks
     */
    <T> T accept(ResponseVisitor<T> visitor);
}
