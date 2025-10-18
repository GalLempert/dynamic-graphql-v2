# ACID Transaction Support

## Overview

This service now supports **ACID transactions** for all write operations, ensuring data consistency and reliability.

## Features

### 1. Transactional Write Operations

All write operations (`CREATE`, `UPDATE`, `DELETE`, `UPSERT`) are automatically wrapped in MongoDB transactions:

- **Atomicity**: All operations in a transaction succeed or fail together
- **Consistency**: Data integrity is maintained across operations
- **Isolation**: Concurrent transactions don't interfere with each other
- **Durability**: Committed data persists even after system failures

### 2. Automatic Audit Fields

Every document automatically includes audit metadata managed by Spring Data:

```java
@Version
private Long version;              // Optimistic locking version

@CreatedDate
private Instant createdAt;         // Auto-set on creation

@LastModifiedDate
private Instant lastModifiedAt;    // Auto-updated on modification

@CreatedBy
private String createdBy;          // User/system that created

@LastModifiedBy
private String lastModifiedBy;     // User/system that last modified
```

### 3. Optimistic Locking

The `@Version` field provides optimistic locking to prevent lost updates:

```
User A reads document (version=1)
User B reads document (version=1)
User A updates document → succeeds (version=2)
User B updates document → FAILS (stale version)
```

## Architecture

### Base Classes

**AuditableBaseDocument** - Abstract base class with audit fields:
```java
@Getter
@Setter
public abstract class AuditableBaseDocument {
    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastModifiedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastModifiedBy;
}
```

**DynamicDocument** - Concrete class for schemaless documents:
```java
@Document
public class DynamicDocument extends AuditableBaseDocument {
    @Id
    private String id;

    private Map<String, Object> dynamicFields;  // User data
}
```

### Transaction Configuration

**MongoConfig** enables transactions and auditing:
```java
@Configuration
@EnableMongoAuditing
@EnableTransactionManagement
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
```

**WriteService** is transactional by default:
```java
@Service
@Transactional  // All methods run in transactions
public class WriteService {

    public WriteResponse executeCreate(CreateRequest request, String collectionName) {
        // If this fails, entire transaction rolls back
        List<String> ids = repository.insertMany(collectionName, documents);
        return new CreateResponse(ids);
    }
}
```

## Requirements

### MongoDB Configuration

Transactions require:
- **MongoDB 4.0 or higher**
- **Replica Set configuration** (not standalone)

### Replica Set Setup

For development:
```bash
# Start MongoDB as a replica set
mongod --replSet rs0

# Initialize replica set
mongo --eval "rs.initiate()"
```

For production:
```bash
# Configure replica set with multiple nodes
rs.initiate({
  _id: "rs0",
  members: [
    { _id: 0, host: "mongo1:27017" },
    { _id: 1, host: "mongo2:27017" },
    { _id: 2, host: "mongo3:27017" }
  ]
})
```

## Usage Examples

### Automatic Transaction Management

All write operations are automatically transactional:

```bash
# CREATE - If validation fails, nothing is inserted
POST /api/users
{
  "documents": [
    {"name": "Alice", "email": "alice@example.com"},
    {"name": "Bob", "email": "bob@example.com"}
  ]
}
# ✅ Both inserted or neither inserted

# UPDATE - If any update fails, all are rolled back
PATCH /api/products?category=electronics
{
  "updates": {"price": 99.99}
}
# ✅ All matching documents updated or none

# DELETE - Atomic bulk deletion
DELETE /api/orders?status=cancelled
# ✅ All matching documents deleted or none
```

### Optimistic Locking Example

```bash
# Step 1: User A fetches document
GET /api/users/123
Response: {
  "_id": "123",
  "name": "Alice",
  "version": 1
}

# Step 2: User B also fetches the same document
GET /api/users/123
Response: {
  "_id": "123",
  "name": "Alice",
  "version": 1
}

# Step 3: User A updates first
PATCH /api/users/123
{
  "updates": {"name": "Alice Smith"}
}
Response: ✅ Success (version incremented to 2)

# Step 4: User B tries to update with stale version
PATCH /api/users/123
{
  "updates": {"name": "Alice Johnson"}
}
Response: ❌ Error - OptimisticLockingFailureException
Message: "Document has been modified by another user"
```

## Audit Trail

Every document tracks who created/modified it and when:

```bash
POST /api/products
{
  "name": "Laptop",
  "price": 999.99
}

# Database stores:
{
  "_id": "abc123",
  "dynamicFields": {
    "name": "Laptop",
    "price": 999.99
  },
  "version": 0,
  "createdAt": "2025-01-15T10:30:00Z",
  "lastModifiedAt": "2025-01-15T10:30:00Z",
  "createdBy": "inventory-service",
  "lastModifiedBy": "inventory-service"
}

# After update:
PATCH /api/products/abc123
{
  "updates": {"price": 899.99}
}

# Database now shows:
{
  "_id": "abc123",
  "dynamicFields": {
    "name": "Laptop",
    "price": 899.99
  },
  "version": 1,                    // Incremented!
  "createdAt": "2025-01-15T10:30:00Z",
  "lastModifiedAt": "2025-01-15T11:45:00Z",  // Updated!
  "createdBy": "inventory-service",
  "lastModifiedBy": "inventory-service"  // Updated!
}
```

## Error Handling

### Transaction Rollback

If any operation fails, the entire transaction rolls back:

```java
@Transactional
public WriteResponse executeCreate(CreateRequest request, String collectionName) {
    // Insert 5 documents
    for (Document doc : documents) {
        repository.insert(doc);  // If this fails on document #3...
    }
    // ... documents #1 and #2 are rolled back (not committed)
}
```

### Optimistic Locking Failures

When a concurrent modification is detected:

```json
{
  "error": "OptimisticLockingFailureException",
  "message": "Document with version 1 was expected but found version 2",
  "solution": "Fetch the latest version and retry your update"
}
```

## Configuration

### Auditor Configuration

By default, the service name is used as the auditor. To customize:

**AuditorAwareImpl.java:**
```java
@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        // Option 1: Use service name
        return Optional.of(System.getenv("SERVICE"));

        // Option 2: Use authenticated user
        // return Optional.ofNullable(
        //     SecurityContextHolder.getContext()
        //         .getAuthentication()
        //         .getName()
        // );

        // Option 3: Use request header
        // return Optional.ofNullable(
        //     RequestContextHolder.currentRequestAttributes()
        //         .getHeader("X-User-Id")
        // );
    }
}
```

### Transaction Timeout

Configure transaction timeout in application properties:

```properties
# Default timeout: 30 seconds
spring.transaction.default-timeout=30

# For long-running operations
spring.transaction.default-timeout=300
```

### Custom Transaction Behavior

Override transaction behavior on specific methods:

```java
@Transactional(
    propagation = Propagation.REQUIRES_NEW,  // Always start new transaction
    isolation = Isolation.SERIALIZABLE,      // Highest isolation level
    timeout = 60,                            // 60 second timeout
    rollbackFor = Exception.class            // Rollback on any exception
)
public WriteResponse executeComplexOperation() {
    // Custom transactional logic
}
```

## Benefits

### Data Consistency
- No partial updates - all operations succeed or fail together
- Optimistic locking prevents lost updates
- Audit trail ensures accountability

### Reliability
- Automatic rollback on errors
- No orphaned or inconsistent data
- Guaranteed data integrity

### Traceability
- Every change tracked with timestamp and user
- Version history for conflict detection
- Complete audit trail for compliance

## Limitations

### Performance Considerations
- Transactions add overhead (~10-20ms per operation)
- Optimistic locking may cause retries under high contention
- Replica set required (cannot use standalone MongoDB)

### Compatibility
- Requires MongoDB 4.0+
- Requires replica set (not single-node)
- Some cloud MongoDB services may have restrictions

## Monitoring

### Transaction Metrics

Monitor transaction health:
```bash
# Check transaction stats
db.serverStatus().transactions

# Monitor failed transactions
db.currentOp({"transaction": true})
```

### Common Issues

**Transaction timeout:**
```
Error: Transaction exceeded maximum time limit
Solution: Increase timeout or optimize queries
```

**No replica set:**
```
Error: Transactions are only supported on replica sets
Solution: Convert standalone MongoDB to replica set
```

**Version conflict:**
```
Error: OptimisticLockingFailureException
Solution: Normal behavior - implement retry logic
```

## Migration Guide

Existing documents without audit fields will be automatically migrated on first update:

```javascript
// Before (legacy document)
{
  "_id": "123",
  "name": "Alice"
}

// After first update (auto-migrated)
{
  "_id": "123",
  "name": "Alice Smith",
  "version": 0,
  "createdAt": null,              // Cannot retroactively determine
  "lastModifiedAt": "2025-01-15T10:30:00Z",
  "createdBy": null,
  "lastModifiedBy": "inventory-service"
}
```

## Best Practices

1. **Keep transactions short** - Minimize operations per transaction
2. **Handle OptimisticLockingFailureException** - Implement retry logic
3. **Use proper isolation levels** - Balance consistency vs performance
4. **Monitor transaction duration** - Detect slow operations
5. **Test with replica set** - Development environment should match production
