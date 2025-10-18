# QueryOrchestrator Reusability Example

## The Power of Separation

By moving orchestration logic to a **service layer**, we can reuse the same business logic across different protocols!

---

## Architecture: Protocol-Agnostic Service Layer

```
┌─────────────────────────────────────────────────────────────────┐
│                    CONTROLLER LAYER (Thin Adapters)              │
│  Protocol-specific: HTTP, GraphQL, gRPC, WebSocket, MQ, etc.    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  RestApiController          GraphQLController      gRPC Service │
│  (HTTP adapter)             (GraphQL adapter)      (gRPC adapt.)│
│                                                                  │
│  • Parse HTTP request       • Parse GraphQL        • Parse gRPC │
│  • Call orchestrator        • Call orchestrator    • Call orch. │
│  • Format HTTP response     • Format GraphQL       • Format gRPC│
│                                                                  │
└────────────┬───────────────────────┬────────────────────┬────────┘
             │                       │                    │
             └───────────────────────┼────────────────────┘
                                     ↓
         ┌───────────────────────────────────────────────────┐
         │         SERVICE LAYER (Business Logic)            │
         │         Protocol-Agnostic Orchestration           │
         ├───────────────────────────────────────────────────┤
         │                                                    │
         │              QueryOrchestrator                     │
         │  • Validate requests                              │
         │  • Execute queries                                │
         │  • Handle errors                                  │
         │  • NO knowledge of HTTP/GraphQL/gRPC              │
         │                                                    │
         └───────────────────┬───────────────────────────────┘
                             │
                ┌────────────┴────────────┐
                │                         │
         ┌──────▼────────┐       ┌───────▼────────┐
         │QueryService   │       │RequestValidator│
         │(Execute)      │       │(Validate)      │
         └───────────────┘       └────────────────┘
```

---

## Example 1: REST API Controller

```java
@Controller
public class RestApiController {

    private final RequestParser requestParser;        // HTTP-specific
    private final QueryOrchestrator queryOrchestrator; // Reusable!
    private final ResponseBuilder responseBuilder;     // HTTP-specific

    public ResponseEntity<?> handleRestRequest(
            String method, String body,
            HttpServletRequest request, Endpoint endpoint) {

        // 1. Parse HTTP → DTO (protocol-specific)
        QueryRequest queryRequest = requestParser.parse(method, body, request, endpoint);

        // 2. Execute business logic (protocol-agnostic!)
        QueryResponse queryResponse = queryOrchestrator.execute(queryRequest, endpoint);

        // 3. Format DTO → HTTP (protocol-specific)
        return responseBuilder.build(queryResponse);
    }
}
```

**Lines of code:** ~25 (thin adapter)
**Business logic:** 0 (all in orchestrator)
**Protocol-specific:** HTTP parsing and formatting only

---

## Example 2: GraphQL Controller

```java
@DgsComponent
public class GraphQLController {

    private final QueryOrchestrator queryOrchestrator; // Same orchestrator!

    @DgsQuery
    public List<Map<String, Object>> collection(@InputArgument String name) {

        // 1. Create QueryRequest (no HTTP parsing needed)
        QueryRequest request = new FullCollectionRequest();
        Endpoint endpoint = lookupEndpoint(name);

        // 2. Same business logic! Same validation! Same execution!
        QueryResponse response = queryOrchestrator.execute(request, endpoint);

        // 3. Format for GraphQL (convert to list of maps)
        return formatForGraphQL(response);
    }

    @DgsQuery
    public List<Map<String, Object>> filteredCollection(
            @InputArgument String name,
            @InputArgument Map<String, Object> filter) {

        // 1. Create FilteredQueryRequest
        FilterRequest filterRequest = new FilterRequest(filter, null);
        QueryRequest request = new FilteredQueryRequest(filterRequest);
        Endpoint endpoint = lookupEndpoint(name);

        // 2. Same orchestrator handles validation and execution!
        QueryResponse response = queryOrchestrator.execute(request, endpoint);

        // 3. Format for GraphQL
        return formatForGraphQL(response);
    }
}
```

**Lines of code:** ~40 (thin adapter)
**Business logic:** 0 (all in orchestrator)
**Protocol-specific:** GraphQL schema and formatting only
**Shared logic:** 100% (via QueryOrchestrator)

---

## Example 3: gRPC Service (Future)

```java
@GrpcService
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final QueryOrchestrator queryOrchestrator; // Same orchestrator!

    @Override
    public void getProducts(ProductRequest request, StreamObserver<ProductResponse> responseObserver) {

        // 1. Parse gRPC request → QueryRequest
        QueryRequest queryRequest = parseGrpcRequest(request);
        Endpoint endpoint = lookupEndpoint(request.getCollection());

        // 2. Same orchestrator! Same business logic!
        QueryResponse queryResponse = queryOrchestrator.execute(queryRequest, endpoint);

        // 3. Format QueryResponse → gRPC response
        ProductResponse grpcResponse = formatForGrpc(queryResponse);
        responseObserver.onNext(grpcResponse);
        responseObserver.onCompleted();
    }
}
```

**Lines of code:** ~30 (thin adapter)
**Business logic:** 0 (all in orchestrator)
**Protocol-specific:** Protobuf parsing and formatting only
**Shared logic:** 100% (via QueryOrchestrator)

---

## Example 4: Kafka Consumer (Future)

```java
@Service
public class QueryEventConsumer {

    private final QueryOrchestrator queryOrchestrator; // Same orchestrator!

    @KafkaListener(topics = "query-requests")
    public void handleQueryEvent(String message) {

        // 1. Parse Kafka message → QueryRequest
        QueryRequest queryRequest = parseKafkaMessage(message);
        Endpoint endpoint = lookupEndpointFromMessage(message);

        // 2. Same orchestrator! No changes needed!
        QueryResponse queryResponse = queryOrchestrator.execute(queryRequest, endpoint);

        // 3. Publish response to response topic
        publishToKafka("query-responses", queryResponse);
    }
}
```

---

## Example 5: WebSocket Handler (Future)

```java
@Component
public class QueryWebSocketHandler extends TextWebSocketHandler {

    private final QueryOrchestrator queryOrchestrator; // Same orchestrator!

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {

        // 1. Parse WebSocket message → QueryRequest
        QueryRequest queryRequest = parseWebSocketMessage(message);
        Endpoint endpoint = extractEndpointFromMessage(message);

        // 2. Same orchestrator!
        QueryResponse queryResponse = queryOrchestrator.execute(queryRequest, endpoint);

        // 3. Send response via WebSocket
        session.sendMessage(new TextMessage(formatForWebSocket(queryResponse)));
    }
}
```

---

## Comparison: With vs Without Service Layer

### Without Service Layer (Old Way)

```
RestApiController (225 lines)
  ├─ Parse HTTP
  ├─ Validate
  ├─ Execute query
  ├─ Format HTTP response
  └─ Error handling

GraphQLController (would be 150 lines)
  ├─ Parse GraphQL
  ├─ Validate (duplicate logic!)
  ├─ Execute query (duplicate!)
  ├─ Format GraphQL response
  └─ Error handling (duplicate!)

gRPC Service (would be 180 lines)
  ├─ Parse gRPC
  ├─ Validate (duplicate again!)
  ├─ Execute query (duplicate again!)
  ├─ Format gRPC response
  └─ Error handling (duplicate again!)

TOTAL: 555 lines, lots of duplication!
```

### With Service Layer (New Way)

```
QueryOrchestrator (80 lines) - SHARED!
  ├─ Validate
  ├─ Execute query
  └─ Error handling

RestApiController (25 lines) - HTTP adapter
  ├─ Parse HTTP
  ├─ Call orchestrator
  └─ Format HTTP response

GraphQLController (40 lines) - GraphQL adapter
  ├─ Parse GraphQL
  ├─ Call orchestrator
  └─ Format GraphQL response

gRPC Service (30 lines) - gRPC adapter
  ├─ Parse gRPC
  ├─ Call orchestrator
  └─ Format gRPC response

TOTAL: 175 lines, zero duplication!
```

**68% code reduction + zero duplication!**

---

## Benefits

### 1. **Code Reuse**
- Business logic written once
- All protocols use the same validation
- All protocols use the same execution logic
- Bugs fixed once, fixed everywhere

### 2. **Consistency**
- All protocols behave the same
- Same validation rules everywhere
- Same error messages everywhere
- Same performance characteristics

### 3. **Testability**
- Test orchestrator once with unit tests
- Test each adapter with simple integration tests
- No need to test business logic multiple times

### 4. **Maintainability**
- Add feature to orchestrator → all protocols get it
- Change validation logic → all protocols updated
- Refactor query execution → no protocol changes needed

### 5. **Flexibility**
- Add new protocol → just create thin adapter
- Remove protocol → just delete adapter
- Change protocol → orchestrator unchanged

---

## Testing Strategy

### Service Layer Tests (Once)
```java
@Test
void testQueryOrchestrator() {
    // Test business logic independently
    QueryRequest request = new FilteredQueryRequest(...);
    Endpoint endpoint = createTestEndpoint();

    QueryResponse response = orchestrator.execute(request, endpoint);

    assertThat(response.isSuccess()).isTrue();
}
```

### Adapter Tests (Simple)
```java
@Test
void testRestController() {
    // Mock orchestrator
    when(orchestrator.execute(any(), any())).thenReturn(successResponse);

    // Test HTTP parsing and formatting only
    ResponseEntity<?> response = controller.handleRestRequest(...);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
}

@Test
void testGraphQLController() {
    // Mock orchestrator
    when(orchestrator.execute(any(), any())).thenReturn(successResponse);

    // Test GraphQL formatting only
    List<Map<String, Object>> result = controller.collection("products");

    assertThat(result).isNotEmpty();
}
```

**Each layer tested independently!**

---

## Real-World Scenario: Adding New Feature

### Scenario: Add pagination to all protocols

**With Service Layer (New Way):**
1. Update `QueryOrchestrator` to handle pagination (1 file)
2. Done! All protocols (REST, GraphQL, gRPC) get pagination automatically

**Without Service Layer (Old Way):**
1. Update `RestApiController` (1 file)
2. Update `GraphQLController` (1 file)
3. Update `gRpcService` (1 file)
4. Ensure all three work the same way (lots of testing)
5. Fix inconsistencies between implementations
6. Hope you didn't introduce bugs

**1 file vs. 3 files, consistent behavior guaranteed!**

---

## Summary

By moving orchestration to a **service layer**, we achieve:

✅ **Code Reuse** - Write business logic once
✅ **Consistency** - All protocols behave the same
✅ **Testability** - Test once, works everywhere
✅ **Maintainability** - Change once, update everywhere
✅ **Flexibility** - Add new protocols easily
✅ **Separation** - Clear boundaries between layers

**Controllers are now just thin adapters:**
- Parse protocol-specific input
- Call orchestrator
- Format protocol-specific output

**Orchestrator contains all business logic:**
- Validation
- Query execution
- Error handling
- Protocol-agnostic

This is **enterprise-grade architecture**! 🎯
