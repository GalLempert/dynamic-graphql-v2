package sigma.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import sigma.model.DynamicDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicPostgresRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private DynamicDocumentJpaRepository crudRepository;

    @Captor
    private ArgumentCaptor<String> sqlCaptor;

    @Captor
    private ArgumentCaptor<MapSqlParameterSource> paramsCaptor;

    private DynamicPostgresRepository repository;
    private ObjectMapper objectMapper;
    private final String TABLE_NAME = "test-collection";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        repository = new DynamicPostgresRepository(jdbcTemplate, crudRepository, objectMapper);
    }

    @Test
    void testFindAll_ReturnsDocuments() {
        // Given
        DynamicDocument doc = new DynamicDocument();
        doc.setId(1L);
        doc.setTableName(TABLE_NAME);
        doc.setDynamicFields(Map.of("key", "value"));
        List<DynamicDocument> expectedDocuments = List.of(doc);
        when(crudRepository.findByTableNameAndNotDeleted(TABLE_NAME)).thenReturn(expectedDocuments);

        // When
        List<Map<String, Object>> result = repository.findAll(TABLE_NAME);

        // Then
        assertEquals(1, result.size());
        assertEquals("value", result.get(0).get("key"));
        verify(crudRepository).findByTableNameAndNotDeleted(TABLE_NAME);
    }

    @Test
    void testFindWithQuery_AddsNotDeletedFilter() {
        // Given
        String whereClause = "data->>'field' = :value";
        Map<String, Object> params = Map.of("value", "test");
        List<DynamicDocument> expectedDocuments = List.of();
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(expectedDocuments);

        // When
        List<Map<String, Object>> result = repository.findWithQuery(TABLE_NAME, whereClause, null, null, null, params);

        // Then
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        String capturedSql = sqlCaptor.getValue();
        assertTrue(capturedSql.contains("is_deleted = false"));
        assertTrue(capturedSql.contains("table_name = :tableName"));
    }

    @Test
    void testFindRaw_DoesNotAddNotDeletedFilter() {
        // Given
        String whereClause = "data->>'field' = :value";
        Map<String, Object> params = Map.of("value", "test");
        List<DynamicDocument> expectedDocuments = List.of();
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(expectedDocuments);

        // When
        List<Map<String, Object>> result = repository.findRaw(TABLE_NAME, whereClause, params);

        // Then
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        String capturedSql = sqlCaptor.getValue();
        assertFalse(capturedSql.contains("is_deleted = false"));
    }

    @Test
    void testFindByIds() {
        // Given
        List<Long> ids = List.of(1L, 2L);
        DynamicDocument doc1 = new DynamicDocument();
        doc1.setId(1L);
        DynamicDocument doc2 = new DynamicDocument();
        doc2.setId(2L);
        List<DynamicDocument> expectedDocuments = List.of(doc1, doc2);
        when(crudRepository.findByIdInAndTableName(ids, TABLE_NAME)).thenReturn(expectedDocuments);

        // When
        List<Map<String, Object>> result = repository.findByIds(TABLE_NAME, ids);

        // Then
        assertEquals(2, result.size());
        verify(crudRepository).findByIdInAndTableName(ids, TABLE_NAME);
    }

    @Test
    void testFindByIds_EmptyList() {
        // Given
        List<Long> ids = Collections.emptyList();

        // When
        List<Map<String, Object>> result = repository.findByIds(TABLE_NAME, ids);

        // Then
        assertTrue(result.isEmpty());
        verify(crudRepository, never()).findByIdInAndTableName(anyList(), anyString());
    }

    @Test
    void testInsertOne() {
        // Given
        DynamicDocument document = new DynamicDocument();
        document.setDynamicFields(Map.of("field", "value"));

        // Mock the JDBC update to return a generated key
        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(2);
            // Simulate key generation - in real test we'd need proper KeyHolder mock
            return 1;
        }).when(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class), any(KeyHolder.class), any(String[].class));

        // When
        Long id = repository.insertOne(TABLE_NAME, document);

        // Then
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(KeyHolder.class), any(String[].class));
        String capturedSql = sqlCaptor.getValue();
        assertTrue(capturedSql.contains("INSERT INTO dynamic_documents"));
        assertTrue(capturedSql.contains("RETURNING id"));
    }

    @Test
    void testUpdate_UpdateMultiple() {
        // Given
        String whereClause = "data->>'status' = :status";
        Map<String, Object> updates = Map.of("field", "newValue");
        Map<String, Object> params = Map.of("status", "active");

        DynamicDocument doc1 = new DynamicDocument();
        doc1.setId(1L);
        doc1.setVersion(1L);
        doc1.setDynamicFields(Map.of("status", "active"));
        DynamicDocument doc2 = new DynamicDocument();
        doc2.setId(2L);
        doc2.setVersion(1L);
        doc2.setDynamicFields(Map.of("status", "active"));

        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(doc1, doc2));

        // When
        int result = repository.update(TABLE_NAME, whereClause, updates, params, true);

        // Then
        assertEquals(2, result);
        // Verify update was called for each document
        verify(jdbcTemplate, times(2)).update(contains("UPDATE dynamic_documents"), any(MapSqlParameterSource.class));
    }

    @Test
    void testUpdate_UpdateFirst() {
        // Given
        String whereClause = "data->>'status' = :status";
        Map<String, Object> updates = Map.of("field", "newValue");
        Map<String, Object> params = Map.of("status", "active");

        DynamicDocument doc = new DynamicDocument();
        doc.setId(1L);
        doc.setVersion(1L);
        doc.setDynamicFields(Map.of("status", "active"));

        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(doc));

        // When
        int result = repository.update(TABLE_NAME, whereClause, updates, params, false);

        // Then
        assertEquals(1, result);
        // Verify select had LIMIT 1
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertTrue(sqlCaptor.getValue().contains("LIMIT 1"));
    }

    @Test
    void testUpsert_InsertNew() {
        // Given
        String whereClause = "data->>'name' = :name";
        Map<String, Object> documentData = Map.of("name", "test", "value", 42);
        Map<String, Object> params = Map.of("name", "test");

        // Return empty list to simulate no existing document
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        doAnswer(invocation -> 1).when(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class), any(KeyHolder.class), any(String[].class));

        // When
        Map<String, Object> result = repository.upsert(TABLE_NAME, whereClause, documentData, params);

        // Then
        assertNotNull(result.get("upsertedId"));
    }

    @Test
    void testUpsert_UpdateExisting() {
        // Given
        String whereClause = "data->>'name' = :name";
        Map<String, Object> documentData = Map.of("name", "test", "value", 42);
        Map<String, Object> params = Map.of("name", "test");

        DynamicDocument existingDoc = new DynamicDocument();
        existingDoc.setId(1L);
        existingDoc.setVersion(1L);
        existingDoc.setDynamicFields(Map.of("name", "test", "value", 10));

        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(existingDoc));

        // When
        Map<String, Object> result = repository.upsert(TABLE_NAME, whereClause, documentData, params);

        // Then
        assertEquals(1L, result.get("matchedCount"));
        assertEquals(1L, result.get("modifiedCount"));
    }

    @Test
    void testDelete_SoftDelete() {
        // Given
        String whereClause = "data->>'name' = :name";
        Map<String, Object> params = Map.of("name", "test");
        String requestId = "req-123";

        DynamicDocument doc = new DynamicDocument();
        doc.setId(1L);
        doc.setVersion(1L);
        doc.setDynamicFields(Map.of("name", "test"));

        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(doc));

        // When
        int result = repository.delete(TABLE_NAME, whereClause, params, false, requestId);

        // Then
        assertEquals(1, result);
        // Verify update was called with is_deleted = true
        verify(jdbcTemplate).update(contains("SET data ="), paramsCaptor.capture());
        MapSqlParameterSource capturedParams = paramsCaptor.getValue();
        assertEquals(true, capturedParams.getValue("isDeleted"));
        assertEquals(requestId, capturedParams.getValue("latestRequestId"));
    }

    @Test
    void testFindById() {
        // Given
        Long id = 1L;
        DynamicDocument expectedDoc = new DynamicDocument();
        expectedDoc.setId(id);
        expectedDoc.setTableName(TABLE_NAME);
        when(crudRepository.findByIdAndTableName(id, TABLE_NAME)).thenReturn(Optional.of(expectedDoc));

        // When
        DynamicDocument result = repository.findById(TABLE_NAME, id);

        // Then
        assertNotNull(result);
        assertEquals(id, result.getId());
    }

    @Test
    void testFindById_NotFound() {
        // Given
        Long id = 1L;
        when(crudRepository.findByIdAndTableName(id, TABLE_NAME)).thenReturn(Optional.empty());

        // When
        DynamicDocument result = repository.findById(TABLE_NAME, id);

        // Then
        assertNull(result);
    }

    @Test
    void testGetNextPageBySequence() {
        // Given
        long startSequence = 0L;
        int batchSize = 10;

        DynamicDocument doc1 = new DynamicDocument();
        doc1.setId(1L);
        doc1.setSequenceNumber(5L);
        doc1.setDynamicFields(Map.of("key", "value1"));

        DynamicDocument doc2 = new DynamicDocument();
        doc2.setId(2L);
        doc2.setSequenceNumber(10L);
        doc2.setDynamicFields(Map.of("key", "value2"));

        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(doc1, doc2));

        // When
        Map<String, Object> result = repository.getNextPageBySequence(TABLE_NAME, startSequence, batchSize);

        // Then
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        assertEquals(2, data.size());
        assertEquals(10L, result.get("nextSequence"));
        assertFalse((Boolean) result.get("hasMore"));

        // Verify the query uses sequence_number
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        String capturedSql = sqlCaptor.getValue();
        assertTrue(capturedSql.contains("sequence_number > :startSequence"));
        assertTrue(capturedSql.contains("ORDER BY sequence_number ASC"));
    }
}
