package iaf.ofek.sigma.dto.response;

/**
 * Visitor interface for response types
 * 
 * Uses the Visitor design pattern to eliminate instanceof checks
 * in ResponseBuilder. Each response type implements accept() and
 * calls the appropriate visit method polymorphically.
 * 
 * Benefits:
 * - No instanceof checks
 * - Type-safe polymorphic dispatch
 * - Easy to add new response types
 * - Follows Open-Closed Principle
 */
public interface ResponseVisitor<T> {
    
    T visitDocumentList(DocumentListResponse response);
    
    T visitSequence(SequenceResponse response);
    
    T visitError(ErrorResponse response);
    
    T visitCreate(CreateResponse response);
    
    T visitUpdate(UpdateResponse response);
    
    T visitDelete(DeleteResponse response);
    
    T visitUpsert(UpsertResponse response);
}
