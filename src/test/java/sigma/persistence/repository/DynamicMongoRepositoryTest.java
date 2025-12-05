package sigma.persistence.repository;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import sigma.model.DynamicDocument;
import sigma.persistence.entity.SequenceCheckpoint;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicMongoRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private SequenceCheckpointRepository checkpointRepository;

    @Mock
    private MongoCollection<Document> mongoCollection;

    @Mock
    private ChangeStreamIterable<Document> changeStreamIterable;

    @Mock
    private AggregateIterable<Document> aggregateIterable;

    @Mock
    private MongoCursor<ChangeStreamDocument<Document>> cursor;

    @Captor
    private ArgumentCaptor<Query> queryCaptor;

    @Captor
    private ArgumentCaptor<Update> updateCaptor;

    @Captor
    private ArgumentCaptor<List<Document>> pipelineCaptor;

    private DynamicMongoRepository repository;
    private final String COLLECTION_NAME = "test-collection";

    @BeforeEach
    void setUp() {
        repository = new DynamicMongoRepository(mongoTemplate, checkpointRepository);
    }

    @Test
    void testFindAll_AddsNotDeletedFilter() {
        // Given
        List<Document> expectedDocuments = List.of(new Document("key", "value"));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq(COLLECTION_NAME)))
                .thenReturn(expectedDocuments);

        // When
        List<Document> result = repository.findAll(COLLECTION_NAME);

        // Then
        assertEquals(expectedDocuments, result);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Document.class), eq(COLLECTION_NAME));

        Query capturedQuery = queryCaptor.getValue();
        assertTrue(capturedQuery.getQueryObject().containsKey("isDeleted"));
        assertEquals(new Document("$ne", true), capturedQuery.getQueryObject().get("isDeleted"));
    }

    @Test
    void testFindWithQuery_AddsNotDeletedFilter() {
        // Given
        Query inputQuery = new Query();
        List<Document> expectedDocuments = List.of(new Document("key", "value"));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq(COLLECTION_NAME)))
                .thenReturn(expectedDocuments);

        // When
        List<Document> result = repository.findWithQuery(COLLECTION_NAME, inputQuery);

        // Then
        assertEquals(expectedDocuments, result);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Document.class), eq(COLLECTION_NAME));

        Query capturedQuery = queryCaptor.getValue();
        assertTrue(capturedQuery.getQueryObject().containsKey("isDeleted"));
        assertEquals(new Document("$ne", true), capturedQuery.getQueryObject().get("isDeleted"));
    }

    @Test
    void testFindRaw_DoesNotAddNotDeletedFilter() {
        // Given
        Query inputQuery = new Query();
        List<Document> expectedDocuments = List.of(new Document("key", "value"));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq(COLLECTION_NAME)))
                .thenReturn(expectedDocuments);

        // When
        List<Document> result = repository.findRaw(COLLECTION_NAME, inputQuery);

        // Then
        assertEquals(expectedDocuments, result);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Document.class), eq(COLLECTION_NAME));

        Query capturedQuery = queryCaptor.getValue();
        assertFalse(capturedQuery.getQueryObject().containsKey("isDeleted"));
    }

    @Test
    void testFindByIds() {
        // Given
        List<Object> ids = List.of("id1", "id2");
        List<Document> expectedDocuments = List.of(new Document("_id", "id1"), new Document("_id", "id2"));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq(COLLECTION_NAME)))
                .thenReturn(expectedDocuments);

        // When
        List<Document> result = repository.findByIds(COLLECTION_NAME, ids);

        // Then
        assertEquals(expectedDocuments, result);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Document.class), eq(COLLECTION_NAME));

        Query capturedQuery = queryCaptor.getValue();
        assertTrue(capturedQuery.getQueryObject().containsKey("_id"));
    }

    @Test
    void testFindByIds_EmptyList() {
        // Given
        List<Object> ids = Collections.emptyList();

        // When
        List<Document> result = repository.findByIds(COLLECTION_NAME, ids);

        // Then
        assertTrue(result.isEmpty());
        verify(mongoTemplate, never()).find(any(), eq(Document.class), anyString());
    }

    @Test
    void testFindNestedDocuments() {
        // Given
        String fatherDocumentPath = "nestedDocs";
        Query query = new Query();
        when(mongoTemplate.getCollection(COLLECTION_NAME)).thenReturn(mongoCollection);
        when(mongoCollection.aggregate(anyList())).thenReturn(aggregateIterable);
        when(aggregateIterable.into(anyList())).thenReturn(new java.util.ArrayList<>());

        // When
        repository.findNestedDocuments(COLLECTION_NAME, query, fatherDocumentPath);

        // Then
        verify(mongoCollection).aggregate(pipelineCaptor.capture());
        List<Document> pipeline = pipelineCaptor.getValue();

        assertFalse(pipeline.isEmpty());
        // Verify stage 1: filter out deleted
        assertEquals(new Document("$match", new Document("isDeleted", new Document("$ne", true))), pipeline.get(0));
        // Verify stage 2: unwind
        assertEquals(new Document("$unwind", "$" + fatherDocumentPath), pipeline.get(1));
        // Verify stage 3: replaceRoot
        assertEquals(new Document("$replaceRoot", new Document("newRoot", "$" + fatherDocumentPath)), pipeline.get(2));
    }

    @Test
    void testInsertOne() {
        // Given
        DynamicDocument document = new DynamicDocument();
        document.setId("test-id");
        when(mongoTemplate.save(document, COLLECTION_NAME)).thenReturn(document);

        // When
        String id = repository.insertOne(COLLECTION_NAME, document);

        // Then
        assertEquals("test-id", id);
        verify(mongoTemplate).save(document, COLLECTION_NAME);
    }

    @Test
    void testInsertMany() {
        // Given
        DynamicDocument doc1 = new DynamicDocument();
        doc1.setId("id1");
        DynamicDocument doc2 = new DynamicDocument();
        doc2.setId("id2");
        List<DynamicDocument> documents = List.of(doc1, doc2);

        when(mongoTemplate.insert(documents, COLLECTION_NAME)).thenReturn(documents);

        // When
        List<String> ids = repository.insertMany(COLLECTION_NAME, documents);

        // Then
        assertEquals(List.of("id1", "id2"), ids);
        verify(mongoTemplate).insert(documents, COLLECTION_NAME);
    }

    @Test
    void testUpdate_UpdateMultiple() {
        // Given
        Query query = new Query();
        Map<String, Object> updates = Map.of("field", "value");
        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getMatchedCount()).thenReturn(2L);
        when(updateResult.getModifiedCount()).thenReturn(2L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(COLLECTION_NAME)))
                .thenReturn(updateResult);

        // When
        UpdateResult result = repository.update(COLLECTION_NAME, query, updates, true);

        // Then
        assertEquals(updateResult, result);
        verify(mongoTemplate).updateMulti(queryCaptor.capture(), updateCaptor.capture(), eq(COLLECTION_NAME));

        // Verify not deleted filter applied
        assertTrue(queryCaptor.getValue().getQueryObject().containsKey("isDeleted"));
        // Verify update fields set
        assertEquals(new Document("field", "value"), updateCaptor.getValue().getUpdateObject().get("$set"));
    }

    @Test
    void testUpdate_UpdateFirst() {
        // Given
        Query query = new Query();
        Map<String, Object> updates = Map.of("field", "value");
        UpdateResult updateResult = mock(UpdateResult.class);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(COLLECTION_NAME)))
                .thenReturn(updateResult);

        // When
        UpdateResult result = repository.update(COLLECTION_NAME, query, updates, false);

        // Then
        assertEquals(updateResult, result);
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(COLLECTION_NAME));
    }

    @Test
    void testUpsert() {
        // Given
        Query query = new Query();
        Map<String, Object> documentData = Map.of("field", "value");
        UpdateResult updateResult = mock(UpdateResult.class);
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(COLLECTION_NAME)))
                .thenReturn(updateResult);

        // When
        UpdateResult result = repository.upsert(COLLECTION_NAME, query, documentData);

        // Then
        assertEquals(updateResult, result);
        verify(mongoTemplate).upsert(queryCaptor.capture(), updateCaptor.capture(), eq(COLLECTION_NAME));
        assertTrue(queryCaptor.getValue().getQueryObject().containsKey("isDeleted"));
    }

    @Test
    void testDelete_SoftDelete() {
        // Given
        Query query = new Query();
        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(COLLECTION_NAME)))
                .thenReturn(updateResult);

        // When
        DeleteResult result = repository.delete(COLLECTION_NAME, query, false, "req-id");

        // Then
        assertEquals(1L, result.getDeletedCount());
        verify(mongoTemplate).updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(COLLECTION_NAME));

        // Verify soft delete
        Document setOperation = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
        assertEquals(true, setOperation.get("isDeleted"));
        assertEquals("req-id", setOperation.get("latestRequestId"));
        assertTrue(updateCaptor.getValue().getUpdateObject().containsKey("$currentDate"));
    }
}
