# ZooKeeper Configuration Setup Guide

## Overview

This guide explains how to configure ZooKeeper for the dynamic GraphQL/REST API with write operations support.

## Directory Structure

```
/{ENV}/{SERVICE}/
├── schemas/                    # JSON Schemas for validation
│   ├── base-types             # Common reusable types
│   ├── user-schema            # User documents
│   ├── product-schema         # Product documents
│   └── order-schema           # Order documents
│
└── endpoints/                  # Dynamic endpoints
    ├── users/
    │   ├── path               # "/api/users"
    │   ├── httpMethod         # "GET"
    │   ├── databaseCollection # "users"
    │   ├── type               # "REST"
    │   ├── sequenceEnabled    # "true"
    │   ├── defaultBulkSize    # "100"
    │   ├── writeMethods       # "POST,PUT,PATCH,DELETE"
    │   ├── schema             # "user-schema:required"
    │   ├── readFilter/        # Filter config for READ operations (queries)
    │   │   ├── name           # "$eq,$regex"
    │   │   ├── email          # "$eq"
    │   │   └── age            # "$eq,$gt,$lt,$gte,$lte"
    │   └── writeFilter/       # Filter config for WRITE operations (updates/deletes)
    │       ├── _id            # "$eq" (always allowed)
    │       └── email          # "$eq" (unique identifier only)
    │
    └── products/
        ├── path               # "/api/products"
        ├── httpMethod         # "GET"
        ├── databaseCollection # "products"
        ├── writeMethods       # "POST,PATCH,DELETE"
        ├── schema             # "product-schema:required"
        ├── readFilter/        # Permissive for complex queries
        │   ├── category       # "$eq,$in"
        │   ├── price          # "$eq,$gt,$lt,$gte,$lte"
        │   └── manufacturer   # "$eq,$in"
        └── writeFilter/       # Restrictive - prevent mass updates
            └── _id            # "$eq" (only allow targeting specific documents)
```

## Step-by-Step Setup

### 1. Create Base Schema

The base-types schema defines common reusable types:

```bash
zkCli.sh create /dev/myservice/schemas/base-types '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "base-types",
  "definitions": {
    "email": {
      "type": "string",
      "format": "email",
      "maxLength": 255
    },
    "positiveInteger": {
      "type": "integer",
      "minimum": 1
    },
    "nonNegativeInteger": {
      "type": "integer",
      "minimum": 0
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    },
    "objectId": {
      "type": "string",
      "pattern": "^[0-9a-fA-F]{24}$"
    },
    "url": {
      "type": "string",
      "format": "uri"
    },
    "phoneNumber": {
      "type": "string",
      "pattern": "^\\+?[1-9]\\d{1,14}$"
    }
  }
}'
```

### 2. Create User Schema

```bash
zkCli.sh create /dev/myservice/schemas/user-schema '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "user-schema",
  "type": "object",
  "required": ["name", "email"],
  "properties": {
    "name": {
      "type": "string",
      "minLength": 1,
      "maxLength": 100,
      "description": "Full name of the user"
    },
    "email": {
      "$ref": "base-types#/definitions/email",
      "description": "User email address"
    },
    "age": {
      "type": "integer",
      "minimum": 0,
      "maximum": 150,
      "description": "User age in years"
    },
    "role": {
      "type": "string",
      "enum": ["admin", "user", "guest"],
      "default": "user",
      "description": "User role"
    },
    "verified": {
      "type": "boolean",
      "default": false,
      "description": "Email verification status"
    },
    "phoneNumber": {
      "$ref": "base-types#/definitions/phoneNumber"
    },
    "address": {
      "type": "object",
      "properties": {
        "street": { "type": "string" },
        "city": { "type": "string" },
        "state": { "type": "string", "minLength": 2, "maxLength": 2 },
        "zipCode": { "type": "string", "pattern": "^\\d{5}$" },
        "country": { "type": "string", "minLength": 2, "maxLength": 2 }
      }
    },
    "metadata": {
      "type": "object",
      "additionalProperties": true,
      "description": "Flexible metadata field"
    }
  },
  "additionalProperties": false
}'
```

### 3. Create Product Schema

```bash
zkCli.sh create /dev/myservice/schemas/product-schema '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "product-schema",
  "type": "object",
  "required": ["name", "sku", "price", "category"],
  "properties": {
    "name": {
      "type": "string",
      "minLength": 1,
      "maxLength": 200
    },
    "description": {
      "type": "string",
      "maxLength": 2000
    },
    "sku": {
      "type": "string",
      "pattern": "^[A-Z0-9]{6,20}$",
      "description": "Stock Keeping Unit"
    },
    "price": {
      "type": "number",
      "minimum": 0,
      "multipleOf": 0.01,
      "description": "Price in USD"
    },
    "category": {
      "type": "string",
      "enum": ["electronics", "clothing", "books", "food", "other"]
    },
    "stock": {
      "$ref": "base-types#/definitions/nonNegativeInteger",
      "description": "Available stock quantity"
    },
    "images": {
      "type": "array",
      "items": {
        "$ref": "base-types#/definitions/url"
      },
      "maxItems": 10
    },
    "tags": {
      "type": "array",
      "items": { "type": "string" },
      "maxItems": 20,
      "uniqueItems": true
    },
    "active": {
      "type": "boolean",
      "default": true
    }
  },
  "additionalProperties": false
}'
```

### 4. Create User Endpoint Configuration

```bash
# Base endpoint properties
zkCli.sh create /dev/myservice/endpoints/users
zkCli.sh create /dev/myservice/endpoints/users/path "/api/users"
zkCli.sh create /dev/myservice/endpoints/users/httpMethod "GET"
zkCli.sh create /dev/myservice/endpoints/users/databaseCollection "users"
zkCli.sh create /dev/myservice/endpoints/users/type "REST"
zkCli.sh create /dev/myservice/endpoints/users/sequenceEnabled "true"
zkCli.sh create /dev/myservice/endpoints/users/defaultBulkSize "100"

# Write configuration
zkCli.sh create /dev/myservice/endpoints/users/writeMethods "POST,PUT,PATCH,DELETE"
zkCli.sh create /dev/myservice/endpoints/users/schema "user-schema:required"

# READ Filter configuration (permissive - for complex queries)
zkCli.sh create /dev/myservice/endpoints/users/readFilter
zkCli.sh create /dev/myservice/endpoints/users/readFilter/name "\$eq,\$regex"
zkCli.sh create /dev/myservice/endpoints/users/readFilter/email "\$eq"
zkCli.sh create /dev/myservice/endpoints/users/readFilter/age "\$eq,\$gt,\$lt,\$gte,\$lte"
zkCli.sh create /dev/myservice/endpoints/users/readFilter/role "\$eq,\$in"
zkCli.sh create /dev/myservice/endpoints/users/readFilter/verified "\$eq"

# WRITE Filter configuration (restrictive - prevent mass updates/deletes)
zkCli.sh create /dev/myservice/endpoints/users/writeFilter
zkCli.sh create /dev/myservice/endpoints/users/writeFilter/email "\$eq"  # Unique identifier

# Note: _id is always filterable with $eq for both read and write, no need to configure
```

### 5. Create Product Endpoint Configuration

```bash
# Base endpoint properties
zkCli.sh create /dev/myservice/endpoints/products
zkCli.sh create /dev/myservice/endpoints/products/path "/api/products"
zkCli.sh create /dev/myservice/endpoints/products/httpMethod "GET"
zkCli.sh create /dev/myservice/endpoints/products/databaseCollection "products"
zkCli.sh create /dev/myservice/endpoints/products/type "REST"
zkCli.sh create /dev/myservice/endpoints/products/sequenceEnabled "false"

# Write configuration
zkCli.sh create /dev/myservice/endpoints/products/writeMethods "POST,PATCH,DELETE"
zkCli.sh create /dev/myservice/endpoints/products/schema "product-schema:required"

# READ Filter configuration (permissive - for complex queries)
zkCli.sh create /dev/myservice/endpoints/products/readFilter
zkCli.sh create /dev/myservice/endpoints/products/readFilter/category "\$eq,\$in"
zkCli.sh create /dev/myservice/endpoints/products/readFilter/price "\$eq,\$gt,\$lt,\$gte,\$lte"
zkCli.sh create /dev/myservice/endpoints/products/readFilter/stock "\$eq,\$gt,\$lt,\$gte,\$lte"
zkCli.sh create /dev/myservice/endpoints/products/readFilter/active "\$eq"
zkCli.sh create /dev/myservice/endpoints/products/readFilter/sku "\$eq"
zkCli.sh create /dev/myservice/endpoints/products/readFilter/manufacturer "\$eq,\$in"

# WRITE Filter configuration (restrictive - only allow targeting by unique identifier)
zkCli.sh create /dev/myservice/endpoints/products/writeFilter
zkCli.sh create /dev/myservice/endpoints/products/writeFilter/sku "\$eq"  # Unique identifier
```

## Configuration Options

### Write Methods

Allowed values (comma-separated):
- `POST` - CREATE operation
- `PUT` - UPSERT operation
- `PATCH` - UPDATE operation
- `DELETE` - DELETE operation

Examples:
```bash
# All write operations
zkCli.sh create /path/writeMethods "POST,PUT,PATCH,DELETE"

# Read-only (no writes)
# Don't create writeMethods node

# Only create and update
zkCli.sh create /path/writeMethods "POST,PATCH"
```

### Schema Reference

Format: `schemaName` or `schemaName:required`

```bash
# Schema validation required
zkCli.sh create /path/schema "user-schema:required"

# Schema validation optional (will validate if present, allow if missing)
zkCli.sh create /path/schema "user-schema"

# No schema validation
# Don't create schema node
```

### Filter Operators

Supported operators:
- `$eq` - Equals
- `$ne` - Not equals
- `$gt` - Greater than
- `$gte` - Greater than or equal
- `$lt` - Less than
- `$lte` - Less than or equal
- `$in` - In array
- `$nin` - Not in array
- `$regex` - Regular expression match
- `$exists` - Field exists
- `$type` - Field type check

Note: `_id` field always allows `$eq` operator by default.

## Verification

### 1. Verify Schema Created

```bash
zkCli.sh get /dev/myservice/schemas/user-schema
```

Should return the JSON schema.

### 2. Verify Endpoint Configuration

```bash
# List all endpoint properties
zkCli.sh ls /dev/myservice/endpoints/users

# Check specific property
zkCli.sh get /dev/myservice/endpoints/users/writeMethods
```

### 3. Verify Filter Configuration

```bash
zkCli.sh ls /dev/myservice/endpoints/users/filter
zkCli.sh get /dev/myservice/endpoints/users/filter/email
```

## Updating Configuration

### Update Schema

```bash
zkCli.sh set /dev/myservice/schemas/user-schema '{...new schema...}'
```

After updating, restart the application or call schema cache eviction API (if implemented).

### Update Write Methods

```bash
zkCli.sh set /dev/myservice/endpoints/users/writeMethods "POST,PATCH"
```

### Update Filter Configuration

```bash
# Add new READ filterable field
zkCli.sh create /dev/myservice/endpoints/users/readFilter/status "\$eq,\$in"

# Update existing READ filter field
zkCli.sh set /dev/myservice/endpoints/users/readFilter/age "\$eq,\$gt,\$gte,\$lt,\$lte"

# Remove READ filter field
zkCli.sh delete /dev/myservice/endpoints/users/readFilter/phoneNumber

# Add WRITE filterable field (use caution - allows mass updates on this field)
zkCli.sh create /dev/myservice/endpoints/users/writeFilter/status "\$eq"

# Remove WRITE filter field (recommended for security)
zkCli.sh delete /dev/myservice/endpoints/users/writeFilter/email
```

## Environment-Specific Configuration

Use different paths for different environments:

```bash
# Development
/dev/myservice/...

# Staging
/staging/myservice/...

# Production
/prod/myservice/...
```

Configure in `application.properties`:
```properties
zookeeper.base-path=/dev/myservice
```

## Troubleshooting

### Schema Not Found Error

**Problem**: "Schema 'user-schema' not found"

**Solutions**:
1. Verify schema exists: `zkCli.sh get /dev/myservice/schemas/user-schema`
2. Check base path in application.properties
3. Restart application to reload schema cache

### Write Method Not Allowed Error

**Problem**: "Write method POST is not allowed for this endpoint"

**Solutions**:
1. Verify writeMethods configured: `zkCli.sh get /path/writeMethods`
2. Check method is in the list: "POST,PUT,PATCH,DELETE"
3. Restart application to reload endpoint configuration

### Filter Validation Error

**Problem**: "Field 'salary' is not filterable on this endpoint"

**Solutions**:
1. For READ: `zkCli.sh create /path/readFilter/salary "\$eq,\$gt,\$lt"`
2. For WRITE (use caution): `zkCli.sh create /path/writeFilter/salary "\$eq"`
3. Or use _id field which is always filterable
4. Restart application

## Separate Read and Write Filters

### Why Separate Filters?

The system uses **separate filter configurations** for read and write operations for critical security and safety reasons:

#### READ Filters (Permissive)
- Allow complex queries with multiple operators
- Support `$or`, `$in`, `$regex` for flexible searching
- Enable users to query large datasets efficiently
- **Example**: "Find all users aged 25-35 in role 'admin' or 'moderator'"

#### WRITE Filters (Restrictive)
- **Prevent mass updates/deletes** by limiting filterable fields
- Typically only allow `_id` and unique identifiers (email, SKU)
- Force users to target specific documents, not broad categories
- **Example**: "Only allow updates/deletes by _id or email"

### Security Benefits

```bash
# READ: Allow broad queries
GET /api/products?category=electronics&price[gt]=100&price[lt]=500
✓ Allowed - complex queries are safe for reads

# WRITE: Prevent accidental mass updates
PATCH /api/products?category=electronics  # Body: {"price": 0}
✗ BLOCKED - 'category' not in writeFilter (would update thousands of products!)

# WRITE: Only allow targeting specific documents
PATCH /api/products?_id=507f1f77bcf86cd799439011  # Body: {"price": 99.99}
✓ Allowed - _id is always allowed for writes

PATCH /api/products?sku=PROD12345  # Body: {"price": 99.99}
✓ Allowed - sku is in writeFilter (unique identifier)
```

### Real-World Example

**Scenario**: Admin wants to update a user's role but accidentally filters by role instead of email:

```bash
# INTENDED (update specific user):
PATCH /api/users?email=alice@example.com
Body: {"role": "admin"}

# ACCIDENTAL (would update ALL guest users):
PATCH /api/users?role=guest
Body: {"role": "admin"}
```

**With separate filters**:
- `readFilter/role` = `$eq,$in` (allow querying by role)
- `writeFilter/role` = NOT configured
- **Result**: Second request is blocked, preventing disaster!

### Configuration Guidelines

**READ Filter - Be Permissive**:
```bash
# Allow complex queries for good UX
zkCli.sh create /path/readFilter/status "\$eq,\$in"
zkCli.sh create /path/readFilter/createdDate "\$gt,\$lt,\$gte,\$lte"
zkCli.sh create /path/readFilter/category "\$eq,\$in"
zkCli.sh create /path/readFilter/name "\$regex"
```

**WRITE Filter - Be Restrictive**:
```bash
# Only allow targeting by unique identifiers
zkCli.sh create /path/writeFilter/email "\$eq"       # Unique
zkCli.sh create /path/writeFilter/sku "\$eq"         # Unique
# DON'T add: category, status, role, etc. (would allow mass updates)
```

**Golden Rule**: If a field appears in writeFilter, users can update/delete ALL documents matching that field value. Only add fields you trust users to filter by for bulk operations.

## Best Practices

1. **Use base-types schema** for common definitions
2. **Always set additionalProperties: false** in schemas for security
3. **Configure only needed write methods** - principle of least privilege
4. **Use required schemas for production** endpoints
5. **Separate read and write filters** - be permissive for reads, restrictive for writes
6. **Write filters should only include unique identifiers** (_id, email, SKU, UUID)
7. **Read filters can include any queryable fields** for good user experience
8. **Document field constraints** in schema descriptions
9. **Use enums for known value sets**
10. **Set sensible min/max constraints**
11. **Index filterable fields** in MongoDB for performance

## Security Considerations

1. **Separate read/write filters** - CRITICAL for preventing mass updates/deletes
2. **Restrict write filters** - only _id and unique identifiers (email, SKU, UUID)
3. **Limit write methods** - only enable what's needed
4. **Require schema validation** in production
5. **Use strict schemas** - `additionalProperties: false`
6. **Validate field formats** - email, URL, phone, etc.
7. **Set size limits** - maxLength, maxItems, etc.
8. **Filter sensitive fields** - don't allow filtering on passwords or tokens
9. **Audit trail** - system automatically tracks with _lastRequestId
10. **Never add category/status/role to writeFilter** - enables dangerous mass operations
