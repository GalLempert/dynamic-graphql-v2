# CLAUDE.md — AI Assistant Guide for dynamic-graphql-v2

## Project Overview

**Dynamic Data Service (Sigma)** — A configuration-driven API gateway that serves REST and GraphQL APIs backed by relational databases (PostgreSQL, Oracle, H2). All endpoint configurations are stored in Apache ZooKeeper, enabling dynamic endpoint creation without code deployments.

- **Language:** Java 21 (records, sealed classes, pattern matching, text blocks)
- **Framework:** Spring Boot 3.5.5
- **GraphQL:** Netflix DGS 10.4.0
- **Build:** Apache Maven 3.8+
- **Root package:** `sigma`

## Build & Run Commands

```bash
mvn clean install          # Full build with tests
mvn test                   # Run all tests
mvn spring-boot:run        # Start the application (port 8080)

# Run a specific test class
mvn test -Dtest=H2FullLifecycleIntegrationTest

# Run with H2 profile (no external DB needed)
mvn spring-boot:run -Dspring.profiles.active=h2
```

**Required environment variables (production):**
- `ENV` — dev|staging|prod
- `SERVICE` — service name
- `ZOOKEEPER_URL` — ZooKeeper connection string (default: localhost:2181)
- `DATABASE_TYPE` — postgresql|oracle|h2 (default: postgresql)

## Project Structure

```
src/main/java/sigma/
├── SigmaApplication.java              # Spring Boot entry point
├── config/                            # Spring configuration beans
├── controller/                        # HTTP/GraphQL thin adapters
│   ├── RestApiController.java         # REST adapter (~180 lines, no business logic)
│   ├── GraphQLEndpointHandler.java    # GraphQL adapter
│   └── ApiController.java             # Dynamic endpoint routing
├── service/                           # Protocol-agnostic business logic
│   ├── Orchestrator.java              # Main entry point for all operations
│   ├── query/                         # Read operations + strategy pattern
│   ├── write/                         # Write operations (CRUD) + sub-entity commands
│   ├── request/                       # HTTP → DTO parsing
│   ├── response/                      # DTO → HTTP formatting
│   ├── validation/                    # Request validation
│   ├── schema/                        # JSON Schema management
│   └── enums/                         # Dynamic enum registry & transformation
├── dto/                               # Strongly-typed data transfer objects
│   ├── request/                       # QueryRequest, WriteRequest hierarchies
│   └── response/                      # QueryResponse, WriteResponse + visitor
├── model/                             # Domain models
│   ├── Endpoint.java                  # Endpoint configuration model
│   ├── filter/                        # Filter tree (composite pattern) + operators
│   │   ├── node/                      # FilterNode, FieldFilterNode, LogicalFilterNode
│   │   └── operator/                  # 15 operator strategies ($eq, $gt, $in, etc.)
│   ├── enums/                         # Dynamic enum models
│   └── schema/                        # JSON Schema models
├── filter/                            # Filter parsing, translation, validation
├── persistence/                       # Data access layer
│   ├── repository/                    # DynamicDocumentRepository (~600 lines)
│   └── dialect/                       # Database dialect strategy (PostgreSQL, Oracle, H2)
├── zookeeper/                         # ZooKeeper config service + watchers
├── observability/                     # Request ID filter (UUIDv7)
└── format/                            # Configurable time formatting
```

## Architecture

### Request Flow

```
HTTP/GraphQL Request
    → Controller (thin adapter: parse → delegate → format)
    → Orchestrator (validate → execute → transform)
    → QueryService or WriteService
    → DynamicDocumentRepository
    → DatabaseDialect (PostgreSQL/Oracle/H2)
    → SQL with JSONB operations
```

### Key Design Principles

1. **Protocol-agnostic service layer** — `Orchestrator` is the central entry point. Controllers are thin adapters that only handle HTTP/GraphQL concerns. The same Orchestrator works for REST, GraphQL, gRPC, WebSocket, or message queue consumers.

2. **No business logic in controllers** — Controllers call `RequestParser.parse()`, then `Orchestrator.executeQuery()`/`executeWrite()`, then `ResponseBuilder.build()`. Nothing else.

3. **Strategy pattern everywhere** — Filter operators, database dialects, query execution strategies, and time formatters all use the strategy pattern.

4. **Composite pattern for filters** — Filter trees use `FilterNode` → `FieldFilterNode` / `LogicalFilterNode` / `CompositeFilterNode`. MongoDB-style operators ($eq, $gt, $in, $and, $or, etc.).

5. **No instanceof checks or switch-on-type** — Polymorphism handles all type dispatching. See `docs/INSTANCEOF_ELIMINATION.md` and `docs/SWITCH_ELIMINATION.md`.

6. **Constructor injection only** — No field injection with `@Autowired`. All dependencies injected via constructor.

7. **JSONB storage** — Documents stored as JSONB in a single `dynamic_documents` table. Schemaless data with typed metadata columns (id, collection, sequence_number, is_deleted, audit fields).

## Testing

### Test Structure

```
src/test/java/sigma/
├── integration/
│   ├── H2FullLifecycleIntegrationTest.java  # Full CRUD lifecycle (ordered tests)
│   ├── H2IntegrationTestConfig.java         # Mocked ZooKeeper beans
│   └── TestEnvironmentInitializer.java      # ENV/SERVICE env var setup
├── service/
│   ├── QueryBuilderTest.java                # Unit: SQL query building
│   └── QueryServiceTest.java               # Unit: query execution
├── controller/
│   └── RestApiControllerTest.java           # MockMvc controller tests
├── filter/
│   └── FilterTranslatorTest.java            # Unit: filter → SQL translation
├── persistence/
│   └── DynamicDocumentRepositoryTest.java   # Repository operations
├── dto/
│   └── WriteRequestTest.java               # DTO construction
├── format/
│   ├── ResponseTimeFormatterTest.java       # Time formatting
│   └── ResponseTimeFormatterBugTest.java    # Edge cases
└── observability/
    └── RequestIdFilterTest.java             # Request ID middleware
```

### Test Conventions

- Integration tests use `@ActiveProfiles("h2")` with in-memory H2 database
- ZooKeeper is fully mocked in tests — no external dependencies required
- Integration tests use `@Order` annotations for sequenced lifecycle testing
- Use `@DisplayName` on all test methods
- Test class names follow `{ClassName}Test.java` pattern
- Integration tests extend nothing — use Spring test annotations directly

## Code Conventions

### Style

- **Lombok** for boilerplate reduction (`@Getter`, `@Builder`, `@RequiredArgsConstructor`, etc.)
- **SLF4J logging** via `LoggerFactory.getLogger(ClassName.class)` — no Lombok `@Slf4j`
- **Java text blocks** (triple-quote `"""`) for multi-line strings and SQL
- **Records** for immutable data carriers where appropriate
- **Sealed classes** for restricted type hierarchies

### Naming

- Package: `sigma.*` (flat hierarchy within functional areas)
- Classes: PascalCase, descriptive (`QueryExecutionStrategyFactory`, `EnumResponseTransformer`)
- Methods: camelCase, verb-first (`executeQuery`, `parseWrite`, `buildError`)
- Constants: UPPER_SNAKE_CASE
- Test methods: descriptive with `@DisplayName` annotation

### Patterns to Follow

- New filter operators: implement `OperatorStrategy` interface, register in `FilterOperator` enum
- New database dialects: implement `DatabaseDialect` interface, add to `DatabaseType` enum and `DatabaseDialectFactory`
- New query types: add to `QueryRequest` hierarchy, implement `accept()` for visitor
- New write operations: add to `WriteRequest` hierarchy, handle in `WriteService`
- New protocols (gRPC, WebSocket): create thin controller adapter, reuse `Orchestrator`

### Patterns to Avoid

- No business logic in controllers
- No `instanceof` checks — use polymorphism
- No `switch` on type — use strategy pattern or polymorphic dispatch
- No field injection (`@Autowired` on fields) — use constructor injection
- No raw SQL string concatenation — use parameterized queries via `JdbcTemplate`

## Key Dependencies

| Dependency | Purpose |
|---|---|
| Spring Boot 3.5.5 | Web framework, DI, actuator |
| Netflix DGS 10.4.0 | GraphQL integration |
| Spring Data JDBC | Database access (JdbcTemplate) |
| Apache ZooKeeper 3.9.3 | Dynamic configuration management |
| Jackson | JSON serialization/deserialization |
| networknt json-schema-validator 1.5.3 | Write request schema validation |
| Micrometer + OpenTelemetry | Metrics, tracing, Prometheus export |
| Logstash Logback Encoder 8.0 | Structured JSON logging |
| Spring Kafka | Event streaming |
| Lombok | Boilerplate reduction |
| H2 | In-memory testing database |
| PostgreSQL / Oracle JDBC | Production database drivers |

## Database

- Single table: `dynamic_documents` with JSONB `data` column
- Metadata columns: `id`, `collection`, `sequence_number`, `is_deleted`, `version`, `created_at`, `last_modified_at`, `created_by`, `last_modified_by`
- Soft deletes: `is_deleted` flag (reads automatically filter deleted docs)
- Audit fields: automatically populated
- Dialect abstraction: `DatabaseDialect` interface with PostgreSQL, Oracle, H2 implementations
- Schema initialization handled by `DatabaseInitializer` using the active dialect

## Configuration (ZooKeeper)

Endpoint configuration tree structure:
```
/{ENV}/{SERVICE}/
├── endpoints/{endpointName}/
│   ├── path, httpMethod, databaseCollection
│   ├── type (REST/GRAPHQL)
│   ├── schema, writeMethods
│   └── readFilter/, writeFilter/
└── dataSource/
    └── enumURL, postgresql/*, oracle/*
```

ZooKeeper watchers automatically reload configuration on changes.

## Documentation

Detailed feature documentation in `docs/`:
- `FINAL_ARCHITECTURE.md` — Full architecture overview
- `WRITE_FEATURE.md` — CRUD operations design
- `FILTER_FEATURE.md` — MongoDB-style filter system
- `PAGINATION_AND_SORTING.md` — Pagination approaches
- `MULTI_DATABASE_SUPPORT.md` — Database dialect system
- `NESTED_DOCUMENT_SUPPORT.md` — Array field operations
- `DYNAMIC_ENUM_MANAGEMENT.md` — External enum integration
- `ZOOKEEPER_SETUP.md` — ZooKeeper configuration guide
- `CONFIGURATION_BASED_VALIDATION.md` — JSON Schema validation
- `ACID_TRANSACTIONS.md` — Transaction guarantees
