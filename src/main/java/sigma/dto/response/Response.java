package sigma.dto.response;

/**
 * Unified interface for all response types (query and write)
 * 
 * This allows polymorphic handling of all responses without instanceof checks.
 * Both QueryResponse and WriteResponse implement this interface through accept().
 * 
 * Design rationale:
 * - QueryResponse is abstract class (has shared state/behavior)
 * - WriteResponse is interface (no shared state, only contract)
 * - Response is unified interface (polymorphic visitor dispatch)
 * 
 * This hierarchy allows:
 * - Type-safe polymorphism via Visitor pattern
 * - No instanceof checks anywhere in the codebase
 * - Clean separation between read and write concerns
 */
public interface Response {
    
    /**
     * Visitor pattern for polymorphic response handling
     * 
     * @param visitor The visitor that will process this response
     * @param <T> The return type of the visitor
     * @return The result of the visit operation
     */
    <T> T accept(ResponseVisitor<T> visitor);
}
