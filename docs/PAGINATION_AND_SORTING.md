# Pagination, Sorting, and Projection

## Overview

Read endpoints support traditional offset pagination, multi-field sorting, projection, and change-stream based sequencing. These capabilities are expressed uniformly in the `FilterRequest` payload or standard query parameters so that REST and future transports share the same orchestration.

## Filter Options API

`FilterRequest` embeds an `options` object with pagination controls that map directly onto Spring Data's `Query` object.【F:src/main/java/sigma/model/filter/FilterRequest.java†L12-L47】

```json
{
  "filter": { "status": "ACTIVE" },
  "options": {
    "sort": { "lastModifiedAt": -1, "_id": 1 },
    "limit": 50,
    "skip": 100,
    "projection": { "_id": 0, "name": 1, "status": 1 }
  }
}
```

- **Sorting** – Negative values apply descending order; positive values apply ascending order. Multiple entries are preserved as a stable list of `Sort.Order` definitions.【F:src/main/java/sigma/filter/FilterTranslator.java†L52-L82】
- **Limit/Skip** – Enforce offset pagination directly on the MongoDB query. These values are validated in endpoint-specific filter policies (see below) and applied through `Query.limit(...)` and `Query.skip(...)` to keep pagination server-side.【F:src/main/java/sigma/filter/FilterTranslator.java†L84-L105】
- **Projection** – Explicit includes/excludes become MongoDB projection documents so clients can trim payloads without bespoke endpoints.【F:src/main/java/sigma/filter/FilterTranslator.java†L107-L118】

Clients preferring GET parameters can send `limit`, `skip`, and `sort` query-string values; the translator maps them onto the same query builder, ensuring consistent behavior across HTTP verbs.【F:src/main/java/sigma/filter/FilterTranslator.java†L34-L50】

## Sequence-Based Pagination

Large collections can be streamed incrementally using change streams. `RequestParser` inspects the `sequence` and `bulkSize` parameters and emits a `SequenceQueryRequest` when present.【F:src/main/java/sigma/service/request/RequestParser.java†L45-L90】

```bash
GET /api/orders?sequence=1125&bulkSize=200
```

1. **Validation** – `RequestValidator` confirms the endpoint has sequence support enabled and enforces minimum/maximum bulk sizes.【F:src/main/java/sigma/service/validation/RequestValidator.java†L39-L71】
2. **Execution** – `SequenceQueryRequest.execute(...)` delegates to `DynamicMongoRepository.getNextPageBySequence`, which opens a change stream, resumes from the stored token if available, and collects `bulkSize` events.【F:src/main/java/sigma/dto/request/SequenceQueryRequest.java†L34-L61】【F:src/main/java/sigma/persistence/repository/DynamicMongoRepository.java†L115-L210】
3. **Response Envelope** – The service returns `data`, `nextSequence`, and `hasMore` so consumers can resume exactly where they left off.【F:src/main/java/sigma/persistence/repository/DynamicMongoRepository.java†L200-L211】

Sequence pagination coexists with traditional filtering: if `sequence` is present, filter bodies are ignored and the change stream feed is used instead. Nested endpoints intentionally disallow sequence queries, preventing ambiguous resume semantics.【F:src/main/java/sigma/service/query/strategy/NestedDocumentQueryStrategy.java†L29-L43】

## Soft Delete Awareness

`DynamicMongoRepository` applies a shared `isDeleted != true` predicate to every query, so pagination and sorting automatically exclude logically deleted rows without extra client logic.【F:src/main/java/sigma/persistence/repository/DynamicMongoRepository.java†L36-L110】【F:src/main/java/sigma/persistence/repository/DynamicMongoRepository.java†L264-L272】

## Tuning Defaults

- `sequenceEnabled` and `defaultBulkSize` live beside endpoint definitions in ZooKeeper; when omitted the registry assigns a conservative default of 100 records per page.【F:src/main/java/sigma/controller/EndpointRegistry.java†L70-L118】
- Operators exposed to clients are constrained by each endpoint's `readFilter` allowlist, meaning you can prevent clients from applying sort or filter fields that bypass indexes. Combine this document with [FILTER_FEATURE.md](FILTER_FEATURE.md) for the full validation story.
