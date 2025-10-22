# Nested Document & Sub-Entity Support

## Overview

Endpoints can expose array fields inside parent documents as first-class collections. This enables querying and mutating nested structures (e.g. order line items) without leaking parent aggregation logic to clients.

Two complementary features deliver this capability:

1. **Nested read endpoints** powered by an aggregation pipeline that unwraps array elements.
2. **Sub-entity orchestration** that manages create/update/delete semantics within nested arrays during write operations.

## Declaring Nested Endpoints

- Set `fatherDocument` on the endpoint to the dot-delimited path of the array field (e.g. `orders.items`).
- Optionally provide a `subEntities` list describing nested collections that require write-time orchestration.
- The registry flags the endpoint as nested, causing the query orchestrator to route requests through the nested strategy.【F:src/main/java/sigma/controller/EndpointRegistry.java†L61-L152】【F:src/main/java/sigma/model/Endpoint.java†L24-L116】

## Read Execution Flow

1. `NestedDocumentQueryStrategy` verifies the endpoint exposes a `fatherDocument` path and refuses unsupported operations like sequence pagination.【F:src/main/java/sigma/service/query/strategy/NestedDocumentQueryStrategy.java†L21-L43】
2. The request's filter options are translated into a standard `Query` object.
3. `DynamicMongoRepository.findNestedDocuments(...)` builds an aggregation pipeline that:
   - Excludes logically deleted parent documents.
   - Unwinds the targeted array field.
   - Promotes the nested element to the top-level document for downstream filtering, projection, sorting, and pagination.【F:src/main/java/sigma/persistence/repository/DynamicMongoRepository.java†L62-L114】
4. The results are returned as if the nested array were a standalone collection, making client consumption straightforward.

## Sub-Entity Write Management

- `WriteService` inspects the endpoint's `subEntities` set and delegates nested array operations to `SubEntityProcessor` before executing repository updates.【F:src/main/java/sigma/service/write/WriteService.java†L37-L210】
- The processor normalizes payloads, generates stable IDs via `UuidSubEntityIdGenerator`, and composes per-collection commands to mutate nested arrays without clobbering sibling elements.【F:src/main/java/sigma/service/write/subentity/SubEntityProcessor.java†L1-L200】
- For upserts, existing documents are loaded first so the processor can merge nested changes and avoid data loss; multi-document updates are rejected when sub-entity operations are requested to preserve deterministic results.【F:src/main/java/sigma/service/write/WriteService.java†L116-L209】

## Example Configuration

```text
/dev/orders/endpoints/order-items/
  path = /orders/{orderId}/items
  httpMethod = GET
  writeMethods = POST,PUT,PATCH,DELETE
  databaseCollection = orders
  fatherDocument = items
  subEntities = items
  schema = order-item-schema:required
```

- Read requests (GET or POST with `filter/options`) return individual order items.
- Write requests automatically scope to the parent order document defined by the filter, while the processor orchestrates nested array updates.

## Operational Notes

- Sequence pagination is intentionally disabled for nested endpoints to avoid ambiguous resume semantics.
- Filters continue to honor endpoint-specific operator allowlists, so administrators can control which nested fields are queryable or mutable.
- Combine this document with [CONFIGURATION_BASED_VALIDATION.md](CONFIGURATION_BASED_VALIDATION.md) for schema guidance and [WRITE_FEATURE.md](WRITE_FEATURE.md) for transaction semantics.
