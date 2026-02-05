package sigma.filter;

import sigma.model.filter.FilterRequest;
import sigma.model.filter.FilterResult;
import sigma.model.filter.SqlPredicate;
import sigma.model.filter.node.FieldFilterNode;
import sigma.model.filter.node.FilterNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilterTranslatorTest {

    @Mock
    private FilterParser filterParser;

    private FilterTranslator filterTranslator;

    @BeforeEach
    void setUp() {
        filterTranslator = new FilterTranslator(filterParser);
    }

    @Test
    void testTranslate_WithFilterAndOptions() {
        // Given
        Map<String, Object> filterMap = Map.of("field", "value");
        Map<String, Integer> sortMap = Map.of("field", 1);
        Map<String, Integer> projectMap = Map.of("field", 1);

        FilterRequest.FilterOptions options = new FilterRequest.FilterOptions();
        options.setSort(sortMap);
        options.setLimit(10);
        options.setSkip(5);
        options.setProjection(projectMap);

        FilterRequest filterRequest = new FilterRequest(filterMap, options);

        FilterNode filterNode = mock(FilterNode.class);
        SqlPredicate predicate = SqlPredicate.jsonbEquals("field", "value", "param0");
        when(filterParser.parse(filterMap)).thenReturn(filterNode);
        when(filterNode.toPredicate()).thenReturn(predicate);

        // When
        FilterResult result = filterTranslator.translate(filterRequest);

        // Then
        assertNotNull(result);
        assertNotNull(result.getWhereClause());
        assertTrue(result.getWhereClause().contains("param0"));
        assertEquals(10, result.getLimit());
        assertEquals(5, result.getOffset());
        assertNotNull(result.getOrderByClause());
    }

    @Test
    void testTranslate_WithEmptyFilter() {
        // Given
        FilterRequest filterRequest = new FilterRequest();

        // When
        FilterResult result = filterTranslator.translate(filterRequest);

        // Then
        assertNotNull(result);
        assertNull(result.getWhereClause());
    }

    @Test
    void testTranslateGetParameters() {
        // Given
        Map<String, String> params = Map.of(
            "name", "Alice",
            "age", "30",
            "limit", "20",
            "skip", "10",
            "sort", "-createdAt"
        );

        // When
        FilterResult result = filterTranslator.translateGetParameters(params);

        // Then
        assertNotNull(result);
        assertNotNull(result.getWhereClause());
        // Check that the where clause contains the field predicates
        assertTrue(result.getWhereClause().contains("name") || result.getParameters().values().stream().anyMatch(v -> "Alice".equals(v)));
        assertTrue(result.getWhereClause().contains("age") || result.getParameters().values().stream().anyMatch(v -> "30".equals(v)));

        assertEquals(20, result.getLimit());
        assertEquals(10, result.getOffset());

        // Check sort
        assertNotNull(result.getOrderByClause());
        assertTrue(result.getOrderByClause().contains("DESC"));
    }

    @Test
    void testTranslateGetParameters_AscendingSort() {
        // Given
        Map<String, String> params = Map.of(
            "sort", "createdAt"
        );

        // When
        FilterResult result = filterTranslator.translateGetParameters(params);

        // Then
        assertNotNull(result.getOrderByClause());
        assertTrue(result.getOrderByClause().contains("ASC"));
    }
}
