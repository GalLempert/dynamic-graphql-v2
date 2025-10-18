package iaf.ofek.sigma.dto.response;

import lombok.Getter;

import java.util.List;

/**
 * Response for errors
 */
@Getter
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

    @Override
    public String getResponseSizeForLogging() {
        return "error";
    }

    @Override
    public <T> T accept(ResponseVisitor<T> visitor) {
        return visitor.visitError(this);
    }
}
