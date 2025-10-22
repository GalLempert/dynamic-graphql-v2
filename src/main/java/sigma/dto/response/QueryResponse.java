package sigma.dto.response;

/**
 * Base class for all query responses
 * 
 * Implements Response interface for unified polymorphic handling
 */
public abstract class QueryResponse implements Response {

    private final boolean success;
    private final String errorMessage;

    protected QueryResponse(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Creates a successful response
     */
    public static <T extends QueryResponse> T success(T response) {
        return response;
    }

    /**
     * Creates an error response
     */
    public static ErrorResponse error(String message) {
        return new ErrorResponse(message);
    }

    /**
     * Template Method pattern for logging response size
     * Polymorphic behavior - no instanceof needed
     */
    public abstract String getResponseSizeForLogging();

    /**
     * Visitor pattern for building HTTP responses
     * Allows ResponseBuilder to handle different response types polymorphically
     */
    public abstract <T> T accept(ResponseVisitor<T> visitor);
}
