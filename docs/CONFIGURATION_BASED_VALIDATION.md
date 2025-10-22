# Configuration-Based Validation

## Overview

Validation rules are defined centrally in ZooKeeper and enforced consistently across read and write paths. Each endpoint carries references to filter allowlists and JSON Schema definitions that are interpreted by dedicated validator components at runtime.

## Validation Building Blocks

| Component | Responsibility |
|-----------|----------------|
| `FilterConfig` | Captures operator allowlists per field for both read and write filters.【F:src/main/java/iaf/ofek/sigma/controller/EndpointRegistry.java†L174-L241】 |
| `FilterValidator` | Parses filter expressions into a node tree and validates operators/structure against the configuration.【F:src/main/java/iaf/ofek/sigma/filter/FilterValidator.java†L15-L52】 |
| `RequestValidator` | Applies read-time rules, including sequence gating and filter validation, before any MongoDB query executes.【F:src/main/java/iaf/ofek/sigma/service/validation/RequestValidator.java†L23-L79】 |
| `WriteValidator` | Ensures write verbs are allowed, filters match the write policy, and payloads satisfy JSON Schema contracts.【F:src/main/java/iaf/ofek/sigma/service/write/WriteValidator.java†L24-L90】 |
| `SchemaValidator` | Loads JSON Schemas from ZooKeeper, augments enum placeholders, and performs Draft 2020-12 validation via the NetworkNT library.【F:src/main/java/iaf/ofek/sigma/service/schema/SchemaValidator.java†L1-L112】 |

## Configuring Filter Policies

1. Under each endpoint create either a `readFilter` or `writeFilter` node.
2. Within the node add a child per field whose value is a comma-separated list of allowed operators (e.g. `$eq,$in,$gte`).
3. Operators are parsed into `FilterOperator` enums; unknown tokens trigger validation errors when requests arrive.【F:src/main/java/iaf/ofek/sigma/controller/EndpointRegistry.java†L183-L229】
4. If a filter is submitted for an endpoint whose filter tree is disabled, the validator immediately rejects it with `Filtering is not enabled for this endpoint`.

## Schema-Backed Write Validation

- Endpoints reference schemas with `schema=product-schema:required`. The suffix selects a named definition inside the JSON file.
- `SchemaManager` loads and caches the schema documents from ZooKeeper, replacing dynamic enum placeholders before handing them to the NetworkNT validator.【F:src/main/java/iaf/ofek/sigma/service/schema/SchemaManager.java†L23-L115】
- `WriteValidator` requests bulk validation for create/upsert payloads and surfaces all failing documents with indexed error prefixes so clients know which array element failed.【F:src/main/java/iaf/ofek/sigma/service/write/WriteValidator.java†L55-L90】
- Update and delete operations skip schema validation (they touch partial documents) but still run filter validation to keep criteria aligned with the write policy.

## Request Lifecycle

1. The controller parses the HTTP payload into a `QueryRequest` or `WriteRequest` DTO.
2. The orchestrator asks either `RequestValidator` or `WriteValidator` to inspect the DTO.
3. Any validation failure short-circuits execution and is converted into a structured error response by `ResponseBuilder`, preventing database calls with invalid filters or payloads.【F:src/main/java/iaf/ofek/sigma/service/Orchestrator.java†L67-L116】
4. Successful validation returns a `ValidationResult.success()` object and the orchestrator proceeds with repository execution.

## Operational Guidance

- Keep filter allowlists minimal to avoid unindexed scans—operators not explicitly listed cannot be used by clients.
- Update schemas in ZooKeeper without restarts; cache eviction happens automatically when enums reload or when administrators trigger manual eviction via the actuator layer.
- Pair this document with [WRITE_FEATURE.md](WRITE_FEATURE.md) for full coverage of transactional semantics and with [DYNAMIC_ENDPOINTS.md](DYNAMIC_ENDPOINTS.md) to see how endpoints declare validation resources.
