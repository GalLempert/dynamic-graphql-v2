# Architecture Diagram: New Layered Design

## Complete Request Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           HTTP REQUEST                                   │
│  POST /api/products                                                      │
│  {                                                                        │
│    "filter": {"price": {"$gte": 100}},                                  │
│    "options": {"limit": 10, "sort": {"price": -1}}                      │
│  }                                                                        │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │
                ┌────────────────▼──────────────────┐
                │   ApiController (Thin Router)     │
                │   - Looks up endpoint             │
                │   - Routes to handler             │
                └────────────────┬──────────────────┘
                                 │
      ╔══════════════════════════▼══════════════════════════╗
      ║         RestApiControllerV2 (Orchestrator)          ║
      ║  ~90 lines | Single Responsibility: Orchestration   ║
      ║                                                      ║
      ║  handleRestRequest() {                              ║
      ║    1. request = requestParser.parse(...)            ║
      ║    2. validation = validator.validate(...)          ║
      ║    3. if (!valid) return error                      ║
      ║    4. response = queryService.execute(...)          ║
      ║    5. return responseBuilder.build(...)             ║
      ║  }                                                   ║
      ╚══════════════════════════════════════════════════════╝
           │              │              │              │
           │              │              │              │
    ┌──────▼────────┐ ┌──▼─────────┐ ┌─▼──────────┐ ┌─▼──────────────┐
    │RequestParser  │ │RequestValid│ │QueryService│ │ResponseBuilder │
    │Deserialize    │ │Validate    │ │Execute     │ │Format          │
    │HTTP → DTO     │ │Rules       │ │Query       │ │DTO → HTTP      │
    └───────┬───────┘ └──────┬─────┘ └─────┬──────┘ └────────────────┘
            │                │               │
            ▼                ▼               │
    ┌───────────────┐ ┌──────────────┐     │
    │  QueryRequest │ │ValidationRes.│     │
    │  (Interface)  │ │- isValid()   │     │
    │               │ │- getErrors() │     │
    │ Implements:   │ └──────────────┘     │
    │ • FullColl.   │                      │
    │ • Filtered    │                      │
    │ • Sequence    │                      │
    └───────────────┘                      │
                                           │
                        ┌──────────────────▼──────────────────┐
                        │     QueryService.execute()          │
                        │     (Polymorphic dispatch)          │
                        │                                     │
                        │  switch (request.getType()) {       │
                        │    case FILTERED ->                 │
                        │      executeFiltered(request)       │
                        │    case SEQUENCE ->                 │
                        │      executeSequence(request)       │
                        │    case FULL_COLLECTION ->          │
                        │      executeFullCollection()        │
                        │  }                                  │
                        └─────────────┬───────────────────────┘
                                      │
                        ┌─────────────▼───────────────┐
                        │      QueryBuilder           │
                        │  Translates request → Query │
                        └─────────────┬───────────────┘
                                      │
                        ┌─────────────▼───────────────┐
                        │   DynamicMongoRepository    │
                        │   Executes MongoDB query    │
                        └─────────────┬───────────────┘
                                      │
                                      ▼
                        ┌──────────────────────────────┐
                        │       MongoDB Database        │
                        │  Returns: List<Document>      │
                        └─────────────┬────────────────┘
                                      │
                        ┌─────────────▼───────────────┐
                        │    QueryResponse (DTO)      │
                        │    • DocumentListResponse   │
                        │    • SequenceResponse       │
                        │    • ErrorResponse          │
                        └─────────────┬───────────────┘
                                      │
                        ┌─────────────▼───────────────┐
                        │    ResponseBuilder          │
                        │    Formats as HTTP          │
                        └─────────────┬───────────────┘
                                      │
                                      ▼
                        ┌──────────────────────────────┐
                        │      HTTP RESPONSE           │
                        │  200 OK                      │
                        │  [                           │
                        │    {"id": 1, "price": 150},  │
                        │    {"id": 2, "price": 120}   │
                        │  ]                           │
                        └──────────────────────────────┘
```

---

## Layer Responsibilities

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CONTROLLER LAYER                             │
│  • Receive HTTP requests                                             │
│  • Orchestrate service calls                                         │
│  • Return HTTP responses                                             │
│  • NO business logic, parsing, or validation                         │
├─────────────────────────────────────────────────────────────────────┤
│  Files: RestApiControllerV2.java, ApiController.java                │
│  Size: ~90 lines                                                     │
└─────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│                       REQUEST PARSING LAYER                          │
│  • Deserialize HTTP → Typed objects                                  │
│  • Extract query parameters                                          │
│  • Parse JSON bodies                                                 │
│  • Create appropriate QueryRequest subtype                           │
├─────────────────────────────────────────────────────────────────────┤
│  Files: RequestParser.java                                           │
│        DTO: QueryRequest, FullCollectionRequest,                     │
│             FilteredQueryRequest, SequenceQueryRequest               │
└─────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│                       VALIDATION LAYER                               │
│  • Validate against endpoint configuration                           │
│  • Check if fields are filterable                                    │
│  • Verify operators are allowed                                      │
│  • Validate sequence parameters                                      │
│  • Return ValidationResult with errors                               │
├─────────────────────────────────────────────────────────────────────┤
│  Files: RequestValidator.java, FilterValidator.java                 │
│  Reusable: Can be injected into any controller                       │
└─────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│                       QUERY SERVICE LAYER                            │
│  • Execute queries (business logic)                                  │
│  • Polymorphic dispatch based on request type                        │
│  • Coordinate QueryBuilder and Repository                            │
│  • Return typed QueryResponse                                        │
├─────────────────────────────────────────────────────────────────────┤
│  Files: QueryService.java (was GraphQLEngine)                        │
└─────────────────────────────────────────────────────────────────────┘
                    ↓                               ↓
┌────────────────────────────────┐  ┌─────────────────────────────────┐
│     QUERY BUILDER LAYER        │  │     REPOSITORY LAYER            │
│  • Translate request → Query   │→ │  • Execute MongoDB operations   │
│  • Build filters, sorts        │  │  • Change Streams               │
│  • Apply pagination            │  │  • Return raw data              │
├────────────────────────────────┤  ├─────────────────────────────────┤
│  Files: QueryBuilder.java      │  │  Files: DynamicMongoRepository  │
│         FilterTranslator.java  │  │                                 │
└────────────────────────────────┘  └─────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│                       RESPONSE BUILDING LAYER                        │
│  • Convert QueryResponse → HTTP ResponseEntity                       │
│  • Format DocumentListResponse as JSON array                         │
│  • Format SequenceResponse with metadata                             │
│  • Format ErrorResponse with details                                 │
│  • Consistent error handling                                         │
├─────────────────────────────────────────────────────────────────────┤
│  Files: ResponseBuilder.java                                         │
│        DTO: QueryResponse, DocumentListResponse,                     │
│             SequenceResponse, ErrorResponse                          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Class Hierarchy

### Request DTOs
```
    ┌──────────────────┐
    │  QueryRequest    │ (interface)
    │  + getType()     │
    └────────┬─────────┘
             │
    ┌────────┴────────────────────────────────┐
    │                                         │
┌───▼───────────────────┐        ┌───────────▼────────────┐
│FullCollectionRequest  │        │FilteredQueryRequest    │
│- None                 │        │- FilterRequest         │
└───────────────────────┘        └────────────────────────┘
                                           │
                        ┌──────────────────▼──────────────┐
                        │  SequenceQueryRequest           │
                        │  - startSequence: long          │
                        │  - bulkSize: int                │
                        └─────────────────────────────────┘
```

### Response DTOs
```
         ┌───────────────────┐
         │  QueryResponse    │ (abstract)
         │  - success: bool  │
         │  - errorMessage   │
         └────────┬──────────┘
                  │
    ┌─────────────┼────────────────────┐
    │             │                    │
┌───▼──────────┐ ┌▼────────────────┐ ┌▼──────────────┐
│DocumentList  │ │SequenceResponse │ │ErrorResponse  │
│Response      │ │- nextSequence   │ │- details: []  │
│- documents:[]│ │- data: []       │ └───────────────┘
└──────────────┘ │- hasMore: bool  │
                 └─────────────────┘
```

---

## Dependency Graph

```
RestApiControllerV2
    ├── depends on → RequestParser
    │                   └── depends on → ObjectMapper
    │
    ├── depends on → RequestValidator
    │                   └── depends on → FilterValidator
    │                                        └── depends on → FilterParser
    │
    ├── depends on → QueryService
    │                   ├── depends on → QueryBuilder
    │                   │                   └── depends on → FilterTranslator
    │                   │                                        └── depends on → FilterParser
    │                   └── depends on → DynamicMongoRepository
    │                                        └── depends on → MongoTemplate
    │
    └── depends on → ResponseBuilder
```

**All dependencies are injected via constructor (Dependency Inversion Principle)**

---

## Request Type Flow Matrix

| Request Type | Parser Output | Validator Checks | Query Builder | Service Method |
|--------------|---------------|------------------|---------------|----------------|
| **GET /api/products** | FullCollectionRequest | None | new Query() | executeFullCollection() |
| **GET /api/products?price=100** | FilteredQueryRequest | Field filterable? | Query with criteria | executeFiltered() |
| **POST /api/products + filter JSON** | FilteredQueryRequest | Field filterable?<br>Operators allowed? | Query with criteria | executeFiltered() |
| **GET /api/products?sequence=100** | SequenceQueryRequest | Sequence enabled?<br>Valid numbers? | null (no Query) | executeSequence() |

---

## Error Handling Flow

```
┌─────────────────────────────────────────────────┐
│  Any layer throws Exception                     │
└─────────────────┬───────────────────────────────┘
                  │
        ┌─────────▼──────────┐
        │  Controller catches │
        └─────────┬───────────┘
                  │
        ┌─────────▼──────────────────────────────────┐
        │  Identifies exception type                  │
        │  • IllegalArgumentException → 400 Bad Req   │
        │  • ValidationException → 400 with details   │
        │  • Other → 500 Internal Error               │
        └─────────┬───────────────────────────────────┘
                  │
        ┌─────────▼──────────────────────┐
        │  responseBuilder.buildError()   │
        └─────────┬───────────────────────┘
                  │
        ┌─────────▼──────────────────────┐
        │  ErrorResponse created          │
        │  {                              │
        │    "error": "message",          │
        │    "details": ["error1", ...]   │
        │  }                              │
        └─────────┬───────────────────────┘
                  │
        ┌─────────▼──────────────────────┐
        │  ResponseEntity<400/500>        │
        └─────────────────────────────────┘
```

---

## Comparison: Lines of Code

### Before (Monolithic)
```
RestApiController.java          225 lines
    ├── handleRestRequest()       55 lines (main method)
    ├── handleFilteredRequest()   40 lines
    ├── handleGetFilterRequest()  47 lines
    ├── hasFilterParameters()      4 lines
    ├── isSpecialParameter()       5 lines
    └── health()                   3 lines

GraphQLEngine.java               82 lines
    ├── queryCollection()         4 lines
    ├── queryDocumentById()       4 lines
    ├── getCollectionCount()      4 lines
    └── queryBySequence()        15 lines

TOTAL: 307 lines (core logic)
```

### After (Layered)
```
RestApiControllerV2.java                      90 lines
    └── handleRestRequest()                   30 lines (clean!)

RequestParser.java                           180 lines
    ├── parse()                               12 lines
    ├── parseSequenceRequest()                15 lines
    ├── parseFilteredPostRequest()            10 lines
    ├── parseFilteredGetRequest()             15 lines
    └── helpers                               50 lines

RequestValidator.java                         95 lines
    ├── validate()                            10 lines
    ├── validateSequenceRequest()             20 lines
    ├── validateFilteredRequest()             15 lines
    └── ValidationResult class                50 lines

QueryService.java                            100 lines
    ├── execute()                             10 lines
    ├── executeFullCollection()                5 lines
    ├── executeFiltered()                      7 lines
    └── executeSequence()                     15 lines

QueryBuilder.java                             45 lines
    ├── build()                                8 lines
    ├── buildFullCollectionQuery()             3 lines
    └── buildFilteredQuery()                   3 lines

ResponseBuilder.java                          80 lines
    ├── build()                               15 lines
    ├── buildDocumentListResponse()            3 lines
    ├── buildSequenceResponse()                8 lines
    └── buildErrorResponse()                  10 lines

TOTAL: 590 lines (but highly organized, testable, reusable)
```

**Yes, more total lines, but:**
- Each file is small and focused (45-180 lines)
- Zero duplication
- Each class testable independently
- Each class reusable in other contexts
- Dramatically better readability

---

## Testing Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                        Unit Tests                            │
├─────────────────────────────────────────────────────────────┤
│  RequestParser_Test                                          │
│  • Test GET parameter parsing                                │
│  • Test POST JSON parsing                                    │
│  • Test sequence parameter extraction                        │
│                                                               │
│  RequestValidator_Test                                       │
│  • Test validation rules                                     │
│  • Test error message generation                             │
│                                                               │
│  QueryBuilder_Test                                           │
│  • Test Query object construction                            │
│  • Mock FilterTranslator                                     │
│                                                               │
│  QueryService_Test                                           │
│  • Test polymorphic dispatch                                 │
│  • Mock repository                                           │
│                                                               │
│  ResponseBuilder_Test                                        │
│  • Test response formatting                                  │
│  • Test error formatting                                     │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     Integration Tests                        │
├─────────────────────────────────────────────────────────────┤
│  RestApiController_IntegrationTest                           │
│  • Test full flow with real services                         │
│  • Use test MongoDB                                          │
│  • Verify end-to-end behavior                                │
└─────────────────────────────────────────────────────────────┘
```

---

## Summary

This layered architecture provides:
- ✅ **Clear separation of concerns**
- ✅ **High testability**
- ✅ **Type safety throughout**
- ✅ **No if-else chains**
- ✅ **Reusable components**
- ✅ **Easy to extend**
- ✅ **SOLID principles**
- ✅ **Clean code**
