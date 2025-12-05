package sigma.filter;

import sigma.model.filter.FilterRequest;
import sigma.model.filter.node.FieldFilterNode;
import sigma.model.filter.node.FilterNode;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

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
        Criteria criteria = Criteria.where("field").is("value");
        when(filterParser.parse(filterMap)).thenReturn(filterNode);
        when(filterNode.toCriteria()).thenReturn(criteria);

        // When
        Query query = filterTranslator.translate(filterRequest);

        // Then
        assertNotNull(query);
        assertEquals(new Document("field", "value"), query.getQueryObject());
        assertEquals(10, query.getLimit());
        assertEquals(5, query.getSkip());

        // Verify sort
        Document sortObject = query.getSortObject();
        assertEquals(1, sortObject.get("field"));

        // Verify projection
        Document fieldsObject = query.getFieldsObject();
        assertEquals(1, fieldsObject.get("field"));
    }

    @Test
    void testTranslate_WithEmptyFilter() {
        // Given
        FilterRequest filterRequest = new FilterRequest();

        // When
        Query query = filterTranslator.translate(filterRequest);

        // Then
        assertNotNull(query);
        assertTrue(query.getQueryObject().isEmpty());
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
        Query query = filterTranslator.translateGetParameters(params);

        // Then
        assertNotNull(query);
        Document queryObject = query.getQueryObject();
        assertEquals("Alice", queryObject.get("name"));
        assertEquals("30", queryObject.get("age"));

        assertEquals(20, query.getLimit());
        assertEquals(10, query.getSkip());

        Document sortObject = query.getSortObject();
        assertEquals(-1, sortObject.get("createdAt"));
    }

    @Test
    void testTranslateGetParameters_AscendingSort() {
        // Given
        Map<String, String> params = Map.of(
            "sort", "createdAt"
        );

        // When
        Query query = filterTranslator.translateGetParameters(params);

        // Then
        Document sortObject = query.getSortObject();
        assertEquals(1, sortObject.get("createdAt"));
    }
}
