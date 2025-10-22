# Write Feature Documentation

## Overview

The write feature enables clients to create, update, delete, and upsert documents through the REST API. It includes:

- **JSON Schema Validation** - Documents are validated against schemas stored in ZooKeeper
- **Automatic Audit Fields** - System-managed fields are automatically injected
- **Filter Support** - Write operations can target specific documents using filters
- **Primary Key Access** - _id field is always filterable for single-document operations
- **HTTP Method Mapping** - POST, PUT, PATCH, DELETE map to different write operations

## Architecture

```
Controller Layer (REST)
    ↓
RequestParser → WriteRequest DTO
    ↓
Orchestrator (Service Layer)
    ├─ WriteValidator
    │   ├─ HTTP Method Check
    │   ├─ Filter Validation
    │   └─ Schema Validation (SchemaValidator → SchemaManager → ZooKeeper)
    └─ WriteService
        ├─ Audit Field Injection (AuditFields)
        ├─ Filter Translation (FilterTranslator)
        └─ Repository Call
            ↓
DynamicMongoRepository
    ↓
MongoDB
```

## ZooKeeper Configuration Structure

```
/{ENV}/{SERVICE}/
├── schemas/                    # JSON Schemas
│   ├── base-types             # Common type definitions
│   ├── user-schema            # User document schema
│   └── product-schema         # Product document schema
│
└── endpoints/
    └── {endpointName}/
        ├── path               # REST path
        ├── httpMethod         # Primary HTTP method (GET)
        ├── databaseCollection # MongoDB collection
        ├── writeMethods       # Allowed write methods (POST,PUT,PATCH,DELETE)
        ├── schema             # Schema reference (schemaName:required)
        └── filter/            # Filterable fields
            ├── name           # $eq,$regex
            └── age            # $eq,$gt,$lt
```

### Example Endpoint Configuration

```
/dev/myservice/endpoints/users/path = "/api/users"
/dev/myservice/endpoints/users/httpMethod = "GET"
/dev/myservice/endpoints/users/databaseCollection = "users"
/dev/myservice/endpoints/users/writeMethods = "POST,PUT,PATCH,DELETE"
/dev/myservice/endpoints/users/schema = "user-schema:required"
/dev/myservice/endpoints/users/filter/name = "$eq,$regex"
/dev/myservice/endpoints/users/filter/email = "$eq"
/dev/myservice/endpoints/users/filter/age = "$eq,$gt,$lt"
```

## JSON Schemas

### Base Types Schema

Defines common reusable types:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "base-types",
  "definitions": {
    "email": {
      "type": "string",
      "format": "email"
    },
    "positiveInteger": {
      "type": "integer",
      "minimum": 1
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

### User Schema Example

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "user-schema",
  "type": "object",
  "required": ["name", "email"],
  "properties": {
    "name": {
      "type": "string",
      "minLength": 1,
      "maxLength": 100
    },
    "email": {
      "$ref": "base-types#/definitions/email"
    },
    "age": {
      "type": "integer",
      "minimum": 0,
      "maximum": 150
    },
    "role": {
      "type": "string",
      "enum": ["admin", "user", "guest"]
    },
    "metadata": {
      "type": "object"
    }
  },
  "additionalProperties": false
}
```

Store in ZooKeeper at: `/dev/myservice/schemas/user-schema`

## Automatic Audit Fields

Every document automatically receives system-managed fields:

| Field | Type | Description | Set On |
|-------|------|-------------|--------|
| `_createdAt` | ISO 8601 timestamp | Document creation time | CREATE, UPSERT (new) |
| `_updatedAt` | ISO 8601 timestamp | Last update time | CREATE, UPDATE, UPSERT |
| `_lastRequestId` | String | Request ID that last modified document | CREATE, UPDATE, UPSERT |
| `_id` | ObjectId | MongoDB primary key | CREATE (auto), UPSERT |

**Security**: Clients cannot set these fields - they are stripped from incoming requests and injected by the system.

**Primary Key**: The `_id` field is ALWAYS filterable with `$eq` operator, even if not configured in ZooKeeper.

## Write Operations

### 1. CREATE (POST)

Creates new document(s).

**HTTP Method**: `POST`

**Single Document**:
```bash
POST /api/users
Content-Type: application/json

{
  "name": "Alice",
  "email": "alice@example.com",
  "age": 30
}
```

**Response**:
```json
{
  "type": "CREATE",
  "success": true,
  "affectedCount": 1,
  "insertedIds": ["507f1f77bcf86cd799439011"]
}
```

**Bulk Insert**:
```bash
POST /api/users
Content-Type: application/json

[
  {"name": "Alice", "email": "alice@example.com"},
  {"name": "Bob", "email": "bob@example.com"}
]
```

**Response**:
```json
{
  "type": "CREATE",
  "success": true,
  "affectedCount": 2,
  "insertedIds": ["507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012"]
}
```

### 2. UPDATE (PATCH)

Updates existing document(s) matching a filter.

**HTTP Method**: `PATCH`

**Update by Primary Key (Single)**:
```bash
PATCH /api/users?_id=507f1f77bcf86cd799439011
Content-Type: application/json

{
  "age": 31
}
```

**Update by Filter (Multiple)**:
```bash
PATCH /api/users?role=guest
Content-Type: application/json

{
  "status": "inactive"
}
```

**Response**:
```json
{
  "type": "UPDATE",
  "success": true,
  "affectedCount": 3,
  "matchedCount": 3,
  "modifiedCount": 3
}
```

**Complex Filter (POST body)**:
```bash
PATCH /api/users
Content-Type: application/json

{
  "filter": {
    "age": { "$gt": 25 },
    "$and": [
      { "role": { "$eq": "user" } }
    ]
  },
  "updates": {
    "verified": true
  }
}
```

### 3. DELETE

Deletes document(s) matching a filter.

**HTTP Method**: `DELETE`

**Delete by Primary Key (Single)**:
```bash
DELETE /api/users?_id=507f1f77bcf86cd799439011
```

**Delete by Filter (Multiple)**:
```bash
DELETE /api/users?status=inactive
```

**Response**:
```json
{
  "type": "DELETE",
  "success": true,
  "affectedCount": 5,
  "deletedCount": 5
}
```

**Complex Filter (Request body)**:
```bash
DELETE /api/users
Content-Type: application/json

{
  "filter": {
    "lastLogin": { "$lt": "2023-01-01T00:00:00Z" },
    "role": { "$eq": "guest" }
  }
}
```

### 4. UPSERT (PUT)

Updates if exists, creates if not.

**HTTP Method**: `PUT`

**Upsert by Primary Key**:
```bash
PUT /api/users?_id=507f1f77bcf86cd799439011
Content-Type: application/json

{
  "name": "Alice Updated",
  "email": "alice@example.com",
  "age": 31
}
```

**Response (Updated)**:
```json
{
  "type": "UPSERT",
  "success": true,
  "affectedCount": 1,
  "wasInserted": false,
  "matchedCount": 1,
  "modifiedCount": 1
}
```

**Response (Inserted)**:
```json
{
  "type": "UPSERT",
  "success": true,
  "affectedCount": 1,
  "wasInserted": true,
  "documentId": "507f1f77bcf86cd799439011"
}
```

**Upsert by Custom Field**:
```bash
PUT /api/users
Content-Type: application/json

{
  "filter": {
    "email": { "$eq": "alice@example.com" }
  },
  "document": {
    "name": "Alice",
    "email": "alice@example.com",
    "age": 30
  }
}
```

## Filter Support in Write Operations

All write operations except CREATE support filters:

### GET Parameter Filters

Simple filters via query parameters:

```bash
# Single field
PATCH /api/users?role=admin
DELETE /api/users?status=inactive

# Multiple fields (AND logic)
PATCH /api/users?role=admin&verified=true

# Always works: Primary key
DELETE /api/users?_id=507f1f77bcf86cd799439011
```

### POST Body Filters

Complex filters via JSON body:

```bash
PATCH /api/users
Content-Type: application/json

{
  "filter": {
    "$and": [
      { "age": { "$gte": 18 } },
      { "role": { "$in": ["user", "admin"] } }
    ]
  },
  "updates": {
    "verified": true
  }
}
```

## Validation

### 1. HTTP Method Validation

**Controller Layer** - Validates that the write method is allowed:

```java
if (!endpoint.isWriteMethodAllowed(httpMethod)) {
    return 405 Method Not Allowed
}
```

### 2. Filter Validation

**Service Layer** - Validates filters against FilterConfig:

- Field must be in configured filterable fields OR be `_id`
- Operator must be allowed for that field
- Primary key (`_id`) always allows `$eq`

### 3. Schema Validation

**Service Layer** - Validates documents against JSON Schema (if configured):

**CREATE**: All documents are validated
**UPDATE**: No full validation (partial updates)
**UPSERT**: Full document is validated
**DELETE**: No validation (no document data)

Example validation error:

```json
{
  "error": "Write validation failed",
  "details": [
    "Document[0]: $.email: does not match format 'email'",
    "Document[1]: $.age: must be at least 0"
  ]
}
```

## Request Flow

### POST (Create) Example

```
1. Client → POST /api/users
   Body: {"name": "Alice", "email": "alice@example.com"}

2. RestApiController
   - Parses HTTP request
   - Extracts request ID from header
   - Creates CreateRequest DTO

3. Orchestrator.executeWrite()
   - Calls WriteValidator.validate()

4. WriteValidator
   - ✓ Check POST is allowed (writeMethods config)
   - ✓ No filter (CREATE doesn't use filters)
   - ✓ Validate against schema "user-schema"

5. WriteService.executeCreate()
   - Inject audit fields:
     {
       "name": "Alice",
       "email": "alice@example.com",
       "_createdAt": "2025-10-18T10:30:00Z",
       "_updatedAt": "2025-10-18T10:30:00Z",
       "_lastRequestId": "req-12345"
     }

6. DynamicMongoRepository.insertOne()
   - Insert into MongoDB

7. Response → Client
   {
     "type": "CREATE",
     "insertedIds": ["507f1f77bcf86cd799439011"]
   }
```

### PATCH (Update) Example

```
1. Client → PATCH /api/users?age=30
   Body: {"age": 31}

2. RestApiController
   - Parse filter from query params: {"age": {"$eq": 30}}
   - Parse updates from body: {"age": 31}
   - Create UpdateRequest DTO

3. Orchestrator.executeWrite()
   - Calls WriteValidator.validate()

4. WriteValidator
   - ✓ Check PATCH is allowed
   - ✓ Validate filter: age field allowed with $eq operator
   - ✓ No full schema validation (partial update)

5. WriteService.executeUpdate()
   - Inject audit fields into updates:
     {
       "age": 31,
       "_updatedAt": "2025-10-18T10:31:00Z",
       "_lastRequestId": "req-12346"
     }
   - Translate filter to MongoDB Query

6. DynamicMongoRepository.update()
   - Update matching documents in MongoDB

7. Response → Client
   {
     "type": "UPDATE",
     "matchedCount": 5,
     "modifiedCount": 5
   }
```

## Error Handling

### HTTP Method Not Allowed

```json
{
  "error": "Write validation failed",
  "details": ["Write method POST is not allowed for this endpoint"]
}
```

**HTTP Status**: 400 Bad Request

### Filter Validation Error

```json
{
  "error": "Write validation failed",
  "details": [
    "Field 'salary' is not filterable on this endpoint",
    "Operator $regex is not allowed for field 'age'"
  ]
}
```

### Schema Validation Error

```json
{
  "error": "Write validation failed",
  "details": [
    "$.email: does not match format 'email'",
    "$.age: must be at least 0",
    "$.role: must be one of [admin, user, guest]"
  ]
}
```

### Schema Not Found

```json
{
  "error": "Write validation failed",
  "details": ["Schema 'user-schema' not found"]
}
```

## Code Structure

### DTOs

**Request DTOs** (`src/main/java/sigma/dto/request/`):
- `WriteRequest.java` - Base interface
- `CreateRequest.java` - CREATE operation
- `UpdateRequest.java` - UPDATE operation
- `DeleteRequest.java` - DELETE operation
- `UpsertRequest.java` - UPSERT operation

**Response DTOs** (`src/main/java/sigma/dto/response/`):
- `WriteResponse.java` - Base interface
- `CreateResponse.java` - CREATE result
- `UpdateResponse.java` - UPDATE result
- `DeleteResponse.java` - DELETE result
- `UpsertResponse.java` - UPSERT result

### Services

**Write Services** (`src/main/java/sigma/service/write/`):
- `WriteService.java` - Executes write operations
- `WriteValidator.java` - Validates write requests

**Schema Services** (`src/main/java/sigma/service/schema/`):
- `SchemaManager.java` - Loads and caches schemas from ZooKeeper
- `SchemaValidator.java` - Validates documents against JSON schemas

**Orchestration** (`src/main/java/sigma/service/`):
- `Orchestrator.java` - Main orchestrator for both read and write

### Models

**Schema Models** (`src/main/java/sigma/model/schema/`):
- `JsonSchema.java` - Represents a JSON schema
- `SchemaReference.java` - Reference from endpoint to schema

**Audit** (`src/main/java/sigma/model/`):
- `AuditFields.java` - System-managed audit field injection

### Repository

**Write Methods** (`src/main/java/sigma/persistence/repository/`):
- `DynamicMongoRepository.insertOne()` - Insert single document
- `DynamicMongoRepository.insertMany()` - Bulk insert
- `DynamicMongoRepository.update()` - Update documents
- `DynamicMongoRepository.upsert()` - Upsert document
- `DynamicMongoRepository.delete()` - Delete documents

## Testing

### Manual Testing

**1. Setup ZooKeeper**:

```bash
# Create schema
zkCli.sh create /dev/myservice/schemas/user-schema '{"type":"object","required":["name","email"],"properties":{"name":{"type":"string"},"email":{"type":"string","format":"email"}}}'

# Configure endpoint
zkCli.sh create /dev/myservice/endpoints/users/writeMethods "POST,PUT,PATCH,DELETE"
zkCli.sh create /dev/myservice/endpoints/users/schema "user-schema:required"
```

**2. Test CREATE**:

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: test-123" \
  -d '{"name":"Alice","email":"alice@example.com"}'
```

**3. Test UPDATE**:

```bash
curl -X PATCH "http://localhost:8080/api/users?_id=507f1f77bcf86cd799439011" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: test-124" \
  -d '{"age":31}'
```

**4. Test DELETE**:

```bash
curl -X DELETE "http://localhost:8080/api/users?_id=507f1f77bcf86cd799439011" \
  -H "X-Request-ID: test-125"
```

**5. Test UPSERT**:

```bash
curl -X PUT "http://localhost:8080/api/users?email=alice@example.com" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: test-126" \
  -d '{"name":"Alice","email":"alice@example.com","age":30}'
```

## Best Practices

1. **Always use schema validation** for production endpoints
2. **Use primary key (`_id`) for single-document operations** - it's always available
3. **Configure granular write permissions** - only allow needed HTTP methods
4. **Filter by indexed fields** for better performance
5. **Use bulk operations** when creating multiple documents
6. **Leverage audit fields** for debugging and compliance
7. **Use UPSERT** for idempotent operations

## Future Enhancements

- [ ] Soft delete support (set a field instead of actual deletion)
- [ ] Transaction support for multi-document operations
- [ ] Optimistic locking with version fields
- [ ] Bulk update/delete limits for safety
- [ ] Write operation hooks/interceptors
- [ ] Custom validators per endpoint
- [ ] Field-level permissions
- [ ] Write operation audit log
