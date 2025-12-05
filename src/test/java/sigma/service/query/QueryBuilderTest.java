package sigma.service.query;

import sigma.dto.request.FilteredQueryRequest;
import sigma.dto.request.QueryRequest;
import sigma.filter.FilterTranslator;
import sigma.model.filter.FilterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryBuilderTest {

    @Mock
    private FilterTranslator filterTranslator;

    private QueryBuilder queryBuilder;

    @BeforeEach
    void setUp() {
        queryBuilder = new QueryBuilder(filterTranslator);
    }

    @Test
    void testBuild_FilteredQueryRequest() {
        // Given
        FilterRequest filterRequest = new FilterRequest();
        FilteredQueryRequest request = new FilteredQueryRequest(filterRequest);
        Query expectedQuery = new Query();
        when(filterTranslator.translate(filterRequest)).thenReturn(expectedQuery);

        // When
        Query result = queryBuilder.build(request);

        // Then
        assertEquals(expectedQuery, result);
        verify(filterTranslator).translate(filterRequest);
    }

    @Test
    void testGetFilterTranslator() {
        assertEquals(filterTranslator, queryBuilder.getFilterTranslator());
    }
}
