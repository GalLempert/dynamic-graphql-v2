package sigma.service.query.strategy;

import sigma.dto.request.QueryRequest;
import sigma.dto.response.DocumentListResponse;
import sigma.dto.response.QueryResponse;
import sigma.model.Endpoint;
import sigma.service.query.QueryService;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Strategy that executes read operations against nested document arrays using aggregation.
 */
@Component
public class NestedDocumentQueryStrategy implements QueryExecutionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(NestedDocumentQueryStrategy.class);

    @Override
    public boolean supports(Endpoint endpoint) {
        return endpoint != null && endpoint.isNestedDocument();
    }

    @Override
    public QueryResponse execute(QueryRequest request, Endpoint endpoint, QueryService service) {
        Assert.notNull(endpoint, "Endpoint must not be null for nested queries");
        String fatherDocument = endpoint.getFatherDocument();
        Assert.hasText(fatherDocument, "Father document path must not be empty");

        logger.debug("Executing nested query for endpoint {} (fatherDocument={})", endpoint.getName(), fatherDocument);

        var query = request.buildQuery(service.getQueryBuilder());
        if (query == null) {
            throw new UnsupportedOperationException("Sequence queries are not supported for nested endpoints");
        }

        List<Document> nestedDocuments = service.getRepository().findNestedDocuments(
                endpoint.getDatabaseCollection(),
                query,
                fatherDocument
        );

        return new DocumentListResponse(nestedDocuments);
    }
}

