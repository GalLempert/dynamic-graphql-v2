# Multi-Database Support Documentation

## Overview

The Dynamic Document Service now supports multiple database backends through a database dialect abstraction layer. This enables deploying the same application to different database environments without code changes - only configuration.

**Supported Databases:**
- **PostgreSQL** - Production-grade with JSONB for optimal JSON document storage
- **Oracle** - Enterprise database with JSON functions (21c+ compatible)
- **H2** - Embedded database for local development and testing

---

## Architecture

### Database Dialect System

The multi-database support is built on an abstraction layer that isolates all database-specific SQL generation:

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Application Layer                             │
│              (QueryService, WriteService, Repository)                │
└────────────────────────────────┬────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      DatabaseDialect Interface                       │
│                                                                       │
│   ┌──────────────────────────────────────────────────────────────┐  │
│   │  • getCreateTableSql()      - Schema DDL                     │  │
│   │  • jsonExtractText()        - JSON field access              │  │
│   │  • jsonEquals(), jsonIn()   - JSON predicates                │  │
│   │  • limitClause()            - Pagination                     │  │
│   │  • getInsertSql()           - DML operations                 │  │
│   └──────────────────────────────────────────────────────────────┘  │
└────────────────────────────────┬────────────────────────────────────┘
                                 │
          ┌──────────────────────┼──────────────────────┐
          ▼                      ▼                      ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ PostgreSqlDialect│  │  OracleDialect   │  │    H2Dialect     │
│                  │  │                  │  │                  │
│ • JSONB operators│  │ • JSON_VALUE     │  │ • H2 JSON funcs  │
│ • ->, ->>        │  │ • JSON_QUERY     │  │ • PostgreSQL mode│
│ • LIMIT/OFFSET   │  │ • FETCH FIRST    │  │ • LIMIT/OFFSET   │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

### Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `DatabaseDialect` | `sigma.persistence.dialect` | Interface defining all DB operations |
| `DatabaseType` | `sigma.persistence.dialect` | Enum: POSTGRESQL, ORACLE, H2 |
| `PostgreSqlDialect` | `sigma.persistence.dialect` | PostgreSQL JSONB implementation |
| `OracleDialect` | `sigma.persistence.dialect` | Oracle JSON functions implementation |
| `H2Dialect` | `sigma.persistence.dialect` | H2 embedded database implementation |
| `DatabaseDialectFactory` | `sigma.persistence.dialect` | Factory with URL auto-detection |
| `DatabaseDialectConfig` | `sigma.persistence.config` | Spring configuration |
| `DatabaseInitializer` | `sigma.persistence.config` | Dialect-aware schema creation |
| `SqlPredicateFactory` | `sigma.persistence` | Dialect-aware SQL predicate generation |

---

## Configuration

### Via ZooKeeper Service Tree

The recommended approach for production is to configure the database type via ZooKeeper:

```
/{ENV}/{SERVICE}/database/
    └── type                    # Value: "postgresql", "oracle", or "h2"
```

### Via Environment Variables

```bash
# Set database type
export DATABASE_TYPE=postgresql   # or: oracle, h2

# PostgreSQL connection
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5432
export POSTGRES_DB=dynamic_graphql
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=secret

# Oracle connection
export ORACLE_URL=jdbc:oracle:thin:@localhost:1521/FREEPDB1
export ORACLE_USER=system
export ORACLE_PASSWORD=oracle

# H2 uses defaults (in-memory)
```

### Via Application Properties

**Default (PostgreSQL):** `application.properties`
```properties
sigma.database.type=${DATABASE_TYPE:postgresql}
spring.datasource.url=jdbc:postgresql://localhost:5432/dynamic_graphql
```

**H2 Profile:** `application-h2.properties`
```properties
sigma.database.type=h2
spring.datasource.url=jdbc:h2:mem:dynamicdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
spring.h2.console.enabled=true
```

**Oracle Profile:** `application-oracle.properties`
```properties
sigma.database.type=oracle
spring.datasource.url=${ORACLE_URL:jdbc:oracle:thin:@localhost:1521/FREEPDB1}
```

### Auto-Detection from JDBC URL

If `sigma.database.type` is not set, the system auto-detects from the JDBC URL:
- `jdbc:postgresql:...` → PostgreSQL
- `jdbc:oracle:...` → Oracle
- `jdbc:h2:...` → H2

---

## Database-Specific Details

### PostgreSQL

**JSON Storage:** JSONB (binary JSON with indexing support)

**JSON Operators:**
```sql
-- Extract as text
data->>'fieldName'

-- Extract as JSON
data->'fieldName'

-- Check field exists
data ? 'fieldName'

-- Contains
data @> '{"key": "value"}'
```

**Example Queries:**
```sql
-- Equality filter
SELECT * FROM dynamic_documents
WHERE data->>'category' = 'electronics'

-- Numeric comparison
SELECT * FROM dynamic_documents
WHERE CAST(data->>'price' AS DOUBLE PRECISION) >= 100

-- IN clause
SELECT * FROM dynamic_documents
WHERE data->>'status' IN ('active', 'pending')
```

**Indexes:**
- B-tree on `table_name`, `is_deleted`, `sequence_number`
- GIN index on `data` JSONB column for JSON queries

---

### Oracle

**JSON Storage:** CLOB with JSON constraint (Oracle 21c+)

**JSON Functions:**
```sql
-- Extract as text
JSON_VALUE(data, '$.fieldName')

-- Extract as JSON
JSON_QUERY(data, '$.fieldName')

-- Check field exists
JSON_EXISTS(data, '$.fieldName')
```

**Example Queries:**
```sql
-- Equality filter
SELECT * FROM dynamic_documents
WHERE JSON_VALUE(data, '$.category') = 'electronics'

-- Numeric comparison
SELECT * FROM dynamic_documents
WHERE TO_NUMBER(JSON_VALUE(data, '$.price')) >= 100

-- IN clause
SELECT * FROM dynamic_documents
WHERE JSON_VALUE(data, '$.status') IN ('active', 'pending')
```

**Boolean Handling:** Oracle uses NUMBER(1) for booleans (0/1)

**Pagination:** Uses `OFFSET n ROWS FETCH FIRST n ROWS ONLY` syntax

---

### H2

**JSON Storage:** VARCHAR/CLOB with JSON functions

**Mode:** Configured with `MODE=PostgreSQL` for compatibility

**JSON Functions:**
```sql
-- Uses H2's JSON functions similar to PostgreSQL
-- Compatible with basic JSONB operations
```

**Use Cases:**
- Local development without database setup
- Unit and integration testing
- CI/CD pipelines
- Quick prototyping

**H2 Console:** Available at `/h2-console` when enabled

---

## Running with Different Databases

### Local Development with H2

```bash
# Using Spring profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2

# Or via environment variable
DATABASE_TYPE=h2 ./mvnw spring-boot:run
```

Access H2 console at: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:dynamicdb`
- User: `sa`
- Password: (empty)

### PostgreSQL

```bash
# Start PostgreSQL (Docker example)
docker run -d --name postgres \
  -e POSTGRES_DB=dynamic_graphql \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 postgres:15

# Run application
./mvnw spring-boot:run
```

### Oracle

```bash
# Start Oracle Free (Docker example)
docker run -d --name oracle \
  -e ORACLE_PASSWORD=oracle \
  -p 1521:1521 container-registry.oracle.com/database/free:latest

# Run application with Oracle profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=oracle
```

---

## Filter Operators

The service uses standardized filter operators (not MongoDB-style):

| Operator | Description | Example |
|----------|-------------|---------|
| `eq` | Equal | `{"category": {"eq": "electronics"}}` |
| `ne` | Not equal | `{"status": {"ne": "deleted"}}` |
| `gt` | Greater than | `{"price": {"gt": 100}}` |
| `gte` | Greater than or equal | `{"price": {"gte": 100}}` |
| `lt` | Less than | `{"price": {"lt": 500}}` |
| `lte` | Less than or equal | `{"price": {"lte": 500}}` |
| `in` | In list | `{"status": {"in": ["active", "pending"]}}` |
| `nin` | Not in list | `{"status": {"nin": ["deleted"]}}` |
| `regex` | Pattern match | `{"name": {"regex": "%phone%"}}` |
| `exists` | Field exists | `{"description": {"exists": true}}` |

### Logical Operators

```json
{
  "filter": {
    "and": [
      {"category": {"eq": "electronics"}},
      {"price": {"lte": 1000}},
      {
        "or": [
          {"brand": {"eq": "Sony"}},
          {"brand": {"eq": "Samsung"}}
        ]
      }
    ]
  }
}
```

---

## Schema Management

The `DatabaseInitializer` component automatically creates the required schema on startup:

### Table Structure

```sql
dynamic_documents (
    id              BIGINT PRIMARY KEY,      -- Auto-generated
    table_name      VARCHAR(255) NOT NULL,   -- Collection/table name
    data            JSONB/CLOB,              -- Document data
    version         BIGINT DEFAULT 0,        -- Optimistic locking
    is_deleted      BOOLEAN DEFAULT FALSE,   -- Soft delete flag
    latest_request_id VARCHAR(255),          -- Idempotency key
    created_by      VARCHAR(255),
    last_modified_by VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE,
    last_modified_at TIMESTAMP WITH TIME ZONE,
    sequence_number BIGINT NOT NULL          -- Change tracking
)
```

### Automatic Schema Initialization

On application startup:
1. `DatabaseInitializer` checks if table exists
2. Creates table using dialect-specific DDL
3. Creates indexes for common query patterns
4. Sets up sequence and trigger for `sequence_number`

---

## Testing

### Unit Tests with H2

```java
@SpringBootTest
@ActiveProfiles("h2")
class DynamicDocumentRepositoryTest {
    // Tests run against H2 in-memory database
}
```

### Running Tests

```bash
# Run all tests with H2
./mvnw test

# Run with specific profile
./mvnw test -Dspring.profiles.active=h2
```

### Integration Testing Against Real Databases

For integration tests against PostgreSQL or Oracle:

```bash
# PostgreSQL integration test
DATABASE_TYPE=postgresql \
POSTGRES_HOST=localhost \
./mvnw test -Dtest=*IntegrationTest

# Oracle integration test
DATABASE_TYPE=oracle \
ORACLE_URL=jdbc:oracle:thin:@localhost:1521/FREEPDB1 \
./mvnw test -Dtest=*IntegrationTest
```

---

## Migration from MongoDB

This service was migrated from MongoDB to the multi-database architecture. Key changes:

### ID System
- **Before:** String-based UUIDs (`_id`)
- **After:** Long sequence-based IDs (`id`)

### Filter Operators
- **Before:** MongoDB-style (`$eq`, `$gt`, `$in`)
- **After:** Standard operators (`eq`, `gt`, `in`)

### Data Access
- **Before:** Spring Data MongoDB with `MongoTemplate`
- **After:** Spring Data JDBC with `NamedParameterJdbcTemplate`

### Repository
- **Before:** `DynamicMongoRepository`
- **After:** `DynamicDocumentRepository` (database-agnostic)

---

## Extending to New Databases

To add support for a new database:

1. **Create Dialect Implementation:**
```java
public class MySqlDialect implements DatabaseDialect {
    @Override
    public DatabaseType getType() {
        return DatabaseType.MYSQL;
    }

    @Override
    public String jsonExtractText(String column, String fieldPath) {
        return String.format("JSON_UNQUOTE(JSON_EXTRACT(%s, '$.%s'))",
            column, escapeFieldPath(fieldPath));
    }
    // ... implement all methods
}
```

2. **Add to DatabaseType enum:**
```java
public enum DatabaseType {
    POSTGRESQL("postgresql"),
    ORACLE("oracle"),
    H2("h2"),
    MYSQL("mysql");  // Add new type
}
```

3. **Update DatabaseDialectFactory:**
```java
public DatabaseDialect createDialect(DatabaseType type) {
    return switch (type) {
        case POSTGRESQL -> new PostgreSqlDialect();
        case ORACLE -> new OracleDialect();
        case H2 -> new H2Dialect();
        case MYSQL -> new MySqlDialect();  // Add new case
    };
}
```

4. **Create application profile:** `application-mysql.properties`

---

## Troubleshooting

### Common Issues

**1. Schema not created:**
- Check `DatabaseInitializer` logs at startup
- Verify database connection properties
- Ensure user has DDL privileges

**2. JSON queries returning null:**
- Verify JSON field path is correct
- Check data is valid JSON
- Use appropriate dialect's JSON functions

**3. Oracle boolean issues:**
- Oracle uses NUMBER(1) for booleans
- The dialect automatically converts true/false to 1/0

**4. H2 compatibility:**
- H2 runs in PostgreSQL mode by default
- Some advanced JSONB features may not work identically

### Logging

Enable dialect logging:
```properties
logging.level.sigma.persistence.dialect=DEBUG
logging.level.sigma.persistence.config=DEBUG
```

---

## Summary

The multi-database support enables:

- **Flexibility** - Deploy to PostgreSQL, Oracle, or H2 without code changes
- **Easy Testing** - Use H2 for rapid local development and CI/CD
- **Enterprise Ready** - Full Oracle support for enterprise deployments
- **Future Proof** - Easy to extend for additional databases
- **Configuration Driven** - Switch databases via ZooKeeper or environment variables

All filter operators, pagination, sorting, and document operations work consistently across all supported databases.
