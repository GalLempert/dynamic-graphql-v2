package iaf.ofek.sigma.dto.response;

/**
 * Base class for all query responses
 */
public abstract class QueryResponse {

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
}
