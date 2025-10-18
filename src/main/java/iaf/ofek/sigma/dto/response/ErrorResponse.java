package iaf.ofek.sigma.dto.response;

import java.util.List;

/**
 * Response for errors
 */
public class ErrorResponse extends QueryResponse {

    private final List<String> details;

    public ErrorResponse(String message) {
        super(false, message);
        this.details = null;
    }

    public ErrorResponse(String message, List<String> details) {
        super(false, message);
        this.details = details;
    }

    public List<String> getDetails() {
        return details;
    }
}
