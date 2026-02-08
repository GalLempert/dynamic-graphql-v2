# Filter Feature Documentation

## Overview

The filter feature allows dynamic filtering of document collections through REST API endpoints. It supports both simple GET parameter filters and complex POST body filters with standard operators. The service supports multiple databases (PostgreSQL, Oracle, H2) - see [MULTI_DATABASE_SUPPORT.md](MULTI_DATABASE_SUPPORT.md).

## Features

- **GET Request Filters**: Simple equality filters via query parameters
- **POST Request Filters**: Complex filters with logical operators via JSON body
- **Validation**: Filters are validated against ZooKeeper configuration
- **Operators**: Supports standard operators like eq, gt, gte, lt, lte, in, and, or, etc.
- **Options**: Sorting, pagination (limit/skip), and field projection
- **Dynamic Configuration**: Filter rules defined in ZooKeeper per endpoint
- **Multi-Database**: Works with PostgreSQL, Oracle, and H2

## ZooKeeper Configuration Structure

### Endpoint with Filter Configuration

```
/{ENV}/{SERVICE}/endpoints/{endpointName}/
    ├── path                    # e.g., "/products"
    ├── httpMethod              # e.g., "GET,POST"
    ├── databaseCollection      # e.g., "products"
    ├── type                    # e.g., "REST"
    ├── sequenceEnabled         # e.g., "false"
    ├── defaultBulkSize         # e.g., "100"
    └── filter/                 # Filter configuration subtree
        ├── category            # Field: category, Value: eq,in
        ├── price               # Field: price, Value: eq,gt,gte,lt,lte
        ├── manufacturer        # Field: manufacturer, Value: eq,in,regex
        └── rating              # Field: rating, Value: eq,gt,gte,lt,lte
```

### Filter Field Configuration Format

Each field under `/filter/` contains a comma-separated list of allowed operators:

```
Field: category
Value: eq,in

Field: price
Value: eq,gt,gte,lt,lte
```

## API Usage

### GET Request with Simple Filters

**URL**: `GET /api/products?category=electronics&price=100`

This creates an equality filter: `{ category: "electronics", price: "100" }`

**With Pagination**:
```
GET /api/products?category=electronics&limit=10&skip=20&sort=-price
```

### POST Request with Complex Filters

**URL**: `POST /api/products`

**Request Body**:
```json
{
  "filter": {
    "and": [
      { "category": { "eq": "electronics" } },
      { "price": { "gte": 100 } },
      {
        "or": [
          { "manufacturer": { "eq": "Sony" } },
          { "rating": { "gt": 4.5 } }
        ]
      }
    ]
  },
  "options": {
    "sort": { "price": -1 },
    "limit": 50,
    "skip": 0,
    "projection": { "name": 1, "price": 1, "id": 0 }
  }
}
```

## Supported Operators

### Comparison Operators
- `eq` - Equal
- `ne` - Not equal
- `gt` - Greater than
- `gte` - Greater than or equal
- `lt` - Less than
- `lte` - Less than or equal
- `in` - In array
- `nin` - Not in array

### Logical Operators
- `and` - Logical AND
- `or` - Logical OR
- `not` - Logical NOT
- `nor` - Logical NOR

### String Operators
- `regex` - Pattern match (SQL LIKE syntax: `%` for wildcard)

### Existence Operators
- `exists` - Field exists
- `type` - Field type check

## Filter Request Structure

### Filter Object
The `filter` object supports nested logical operators and field conditions:

```json
{
  "filter": {
    "fieldName": { "eq": "value" },         // Equality with operator
    "fieldName": { "gt": 100 },             // Comparison operator
    "and": [                                // Logical operator
      { "field1": { "eq": "value1" } },
      { "field2": { "lt": 50 } }
    ]
  }
}
```

### Options Object
```json
{
  "options": {
    "sort": {
      "field1": 1,      // Ascending
      "field2": -1      // Descending
    },
    "limit": 50,        // Max results
    "skip": 0,          // Skip results (pagination)
    "projection": {     // Field selection
      "field1": 1,      // Include
      "field2": 0       // Exclude
    }
  }
}
```

## Validation

Filters are validated against the ZooKeeper configuration:

1. **Filter Enabled Check**: Verifies filtering is enabled for the endpoint
2. **Field Check**: Ensures all fields in the filter are configured as filterable
3. **Operator Check**: Verifies operators are allowed for each field
4. **Structure Check**: Validates logical operator structure (arrays for and/or, objects for not)

### Validation Error Response
```json
{
  "error": "Filter validation failed",
  "details": [
    "Field 'invalidField' is not filterable",
    "Operator regex is not allowed for field 'price'. Allowed: [eq, gt, gte, lt, lte]"
  ]
}
```

## Implementation Flow

### Request Flow
1. **Controller** (`RestApiController`): Receives request
2. **Validation** (`FilterValidator`): Validates filter against endpoint config
3. **Translation** (`FilterTranslator`): Converts filter to SQL predicates using `SqlPredicateFactory`
4. **Engine** (`GraphQLEngine`): Orchestrates query execution
5. **Repository** (`DynamicDocumentRepository`): Executes database query (PostgreSQL, Oracle, or H2)

### Component Architecture

```
Controller Layer (RestApiController)
    ├── Parse request (GET params or POST body)
    ├── Validate filter (FilterValidator)
    └── Translate filter (FilterTranslator → SqlPredicateFactory)
        ↓
Engine Layer (GraphQLEngine)
    └── Execute filtered query
        ↓
Repository Layer (DynamicDocumentRepository)
    └── SQL query execution (via DatabaseDialect)
        ↓
Database (PostgreSQL / Oracle / H2)
```

## Example Configurations

### Example 1: E-commerce Product Endpoint

**ZooKeeper**:
```
/dev/myservice/endpoints/products/
    ├── path: "/products"
    ├── httpMethod: "GET,POST"
    ├── databaseCollection: "products"
    ├── type: "REST"
    └── filter/
        ├── category: eq,in
        ├── price: eq,gt,gte,lt,lte
        ├── brand: eq,in,regex
        ├── inStock: eq
        └── rating: eq,gt,gte,lt,lte
```

**Sample Request**:
```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "and": [
        { "category": { "eq": "electronics" } },
        { "price": { "lte": 1000 } },
        { "inStock": { "eq": true } },
        { "rating": { "gte": 4.0 } }
      ]
    },
    "options": {
      "sort": { "price": 1 },
      "limit": 20
    }
  }'
```

### Example 2: User Search Endpoint

**ZooKeeper**:
```
/dev/myservice/endpoints/users/
    ├── path: "/users"
    ├── httpMethod: "GET,POST"
    ├── databaseCollection: "users"
    ├── type: "REST"
    └── filter/
        ├── username: eq,regex
        ├── email: eq,regex
        ├── status: eq,in
        ├── createdAt: gt,gte,lt,lte
        └── role: eq,in
```

**Sample Request**:
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "or": [
        { "username": { "regex": "%admin%" } },
        { "role": { "in": ["admin", "moderator"] } }
      ],
      "status": { "eq": "active" }
    },
    "options": {
      "projection": { "password": 0 },
      "sort": { "createdAt": -1 }
    }
  }'
```

## Error Handling

### Common Errors

1. **Filtering Not Enabled**
   ```json
   {
     "error": "Filter validation failed",
     "details": ["Filtering is not enabled for this endpoint"]
   }
   ```

2. **Invalid Field**
   ```json
   {
     "error": "Filter validation failed",
     "details": ["Field 'unknownField' is not filterable"]
   }
   ```

3. **Invalid Operator**
   ```json
   {
     "error": "Filter validation failed",
     "details": ["Operator regex is not allowed for field 'price'. Allowed: [eq, gt, gte, lt, lte]"]
   }
   ```

4. **Invalid JSON**
   ```json
   {
     "error": "Invalid filter format: Unexpected character...",
   }
   ```

## Notes

- GET parameter filters only support simple equality (implicit `eq`)
- POST body filters support full query syntax with standard operators
- Filter validation happens before query execution
- Invalid filters return 400 Bad Request with detailed error messages
- Empty filters are valid and return all documents (subject to options like limit)
- Special parameters (`sequence`, `bulkSize`, `limit`, `skip`, `sort`) are not treated as filter fields
- All filters work consistently across PostgreSQL, Oracle, and H2 databases
- See [MULTI_DATABASE_SUPPORT.md](MULTI_DATABASE_SUPPORT.md) for database-specific details
