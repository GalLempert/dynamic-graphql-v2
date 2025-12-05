package sigma.service.query;

import sigma.dto.request.QueryRequest;
import sigma.dto.response.QueryResponse;
import sigma.model.Endpoint;
import sigma.persistence.repository.DynamicMongoRepository;
import sigma.service.query.strategy.QueryExecutionStrategy;
import sigma.service.query.strategy.QueryExecutionStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryServiceTest {

    @Mock
    private DynamicMongoRepository mongoRepository;

    @Mock
    private QueryBuilder queryBuilder;

    @Mock
    private QueryExecutionStrategyFactory strategyFactory;

    @Mock
    private QueryExecutionStrategy executionStrategy;

    @Mock
    private Endpoint endpoint;

    @Mock
    private QueryRequest queryRequest;

    @Mock
    private QueryResponse queryResponse;

    private QueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new QueryService(mongoRepository, queryBuilder, strategyFactory);
    }

    @Test
    void testExecute_DelegatesToStrategy() {
        // Given
        when(endpoint.getName()).thenReturn("test-endpoint");
        when(queryRequest.getType()).thenReturn(QueryRequest.QueryType.FILTERED);
        when(strategyFactory.getStrategy(endpoint)).thenReturn(executionStrategy);
        when(executionStrategy.execute(queryRequest, endpoint, queryService)).thenReturn(queryResponse);

        // When
        QueryResponse result = queryService.execute(queryRequest, endpoint);

        // Then
        assertNotNull(result);
        assertEquals(queryResponse, result);
        verify(strategyFactory).getStrategy(endpoint);
        verify(executionStrategy).execute(queryRequest, endpoint, queryService);
    }

    @Test
    void testExecute_WithNullEndpoint_DelegatesToStrategy() {
        // Given
        Endpoint nullEndpoint = null;
        when(queryRequest.getType()).thenReturn(QueryRequest.QueryType.FILTERED);
        when(strategyFactory.getStrategy(nullEndpoint)).thenReturn(executionStrategy);
        when(executionStrategy.execute(queryRequest, nullEndpoint, queryService)).thenReturn(queryResponse);

        // When
        QueryResponse result = queryService.execute(queryRequest, nullEndpoint);

        // Then
        assertNotNull(result);
        assertEquals(queryResponse, result);
        verify(strategyFactory).getStrategy(nullEndpoint);
        verify(executionStrategy).execute(queryRequest, nullEndpoint, queryService);
    }

    @Test
    void testGetRepository() {
        assertEquals(mongoRepository, queryService.getRepository());
    }

    @Test
    void testGetQueryBuilder() {
        assertEquals(queryBuilder, queryService.getQueryBuilder());
    }
}
