package iaf.ofek.sigma.service.query.strategy;

import iaf.ofek.sigma.dto.request.QueryRequest;
import iaf.ofek.sigma.dto.response.QueryResponse;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.service.query.QueryService;
import org.springframework.stereotype.Component;

/**
 * Default strategy used for querying root-level collections.
 */
@Component
public class RootDocumentQueryStrategy implements QueryExecutionStrategy {

    @Override
    public boolean supports(Endpoint endpoint) {
        return endpoint == null || !endpoint.isNestedDocument();
    }

    @Override
    public QueryResponse execute(QueryRequest request, Endpoint endpoint, QueryService service) {
        String collectionName = endpoint != null ? endpoint.getDatabaseCollection() : null;
        return request.execute(service, collectionName);
    }
}

