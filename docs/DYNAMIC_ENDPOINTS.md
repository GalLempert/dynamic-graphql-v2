# Dynamic Endpoint Creation

## Overview

Dynamic endpoint creation lets the gateway expose new REST or GraphQL surfaces without redeploying the application. Endpoint definitions live entirely in ZooKeeper and are materialized at runtime into `Endpoint` descriptors consumed by the orchestrator layer.

Key capabilities:

- Runtime discovery of all endpoint metadata (path, HTTP method, backing collection, protocol type, pagination defaults, nested document mapping, schema association, allowed write verbs, filter policies).
- Automatic refresh when ZooKeeper nodes change thanks to watchers placed on the entire service tree.
- Zero hard-coded routing logic inside Spring controllers—`RestApiController` simply looks up the `Endpoint` for an incoming request and delegates to the orchestrator.

## Configuration Layout

Endpoint metadata is stored under `/{ENV}/{SERVICE}/endpoints/{endpointName}`. Each endpoint node can expose the following children:

| Node | Description |
|------|-------------|
| `path` | HTTP path (e.g. `/users`). |
| `httpMethod` | Allowed read methods (e.g. `GET,POST`). |
| `writeMethods` | Optional comma-separated list of write verbs (e.g. `POST,PUT,PATCH,DELETE`). |
| `databaseCollection` | MongoDB collection backing the endpoint. |
| `type` | `REST` or `GRAPHQL` (kept for future protocol expansion). |
| `sequenceEnabled` | Enables change-stream pagination. |
| `defaultBulkSize` | Default batch size for sequence queries. |
| `readFilter/*` | Field-level operator allowlist for reads. |
| `writeFilter/*` | Field-level operator allowlist for writes. |
| `schema` | Schema reference such as `user-schema:required`. |
| `subEntities` | Optional comma/newline-separated list of nested array field names. |
| `fatherDocument` | Dot-delimited path to the array field exposed as a nested collection. |

The registry expects configuration nodes to exist under the environment/service path computed from the `ENV` and `SERVICE` environment variables. Misconfigured or missing nodes are logged and skipped so that a single bad endpoint does not block the rest of the tree.

## Runtime Flow

1. **Configuration bootstrap** – `ZookeeperConfigService` loads the complete service, data source, and globals trees and registers recursive watchers for future updates.【F:src/main/java/sigma/zookeeper/ZookeeperConfigService.java†L14-L109】【F:src/main/java/sigma/zookeeper/ZookeeperWatcher.java†L18-L90】
2. **Endpoint materialization** – During startup `EndpointRegistry.loadEndpoints()` groups raw ZooKeeper values into strongly typed `Endpoint` records, including filter policies, schema references, sub-entity metadata, and nested document mappings.【F:src/main/java/sigma/controller/EndpointRegistry.java†L31-L153】
3. **Request handling** – `RestApiController` and other adapters ask the registry for the `Endpoint` matching the incoming method/path pair and pass it to the `Orchestrator` for validation and execution.【F:src/main/java/sigma/controller/RestApiController.java†L24-L133】
4. **Hot updates** – When ZooKeeper emits a change event, `ZookeeperWatcher` reloads the node and updates the in-memory configuration map, allowing higher layers to rebuild affected `Endpoint` objects on demand.【F:src/main/java/sigma/zookeeper/ZookeeperWatcher.java†L24-L90】

## Adding or Updating Endpoints

1. Create or update the endpoint nodes in ZooKeeper under the desired environment/service branch.
2. Optionally define `readFilter`/`writeFilter` rules to constrain operators per field.
3. Provide a `schema` entry if write validation should enforce a JSON Schema.
4. Use `subEntities` and `fatherDocument` to enable nested sub-collection access.
5. The application will detect the new configuration immediately without a restart.

## Failure Handling & Observability

- Missing required properties (`path`, `httpMethod`, `databaseCollection`) are logged and the endpoint is skipped.【F:src/main/java/sigma/controller/EndpointRegistry.java†L61-L120】
- All configuration mutations are logged with the path and new value, making it easy to trace updates coming from ZooKeeper administrators.【F:src/main/java/sigma/zookeeper/ZookeeperConfigService.java†L111-L141】
- Endpoint cache contents can be inspected through `EndpointRegistry.getAllEndpoints()` for debugging or diagnostics.【F:src/main/java/sigma/controller/EndpointRegistry.java†L155-L172】

## Next Steps

- Pair this document with [WRITE_FEATURE.md](WRITE_FEATURE.md) and [FILTER_FEATURE.md](FILTER_FEATURE.md) for details on enforcing the policies referenced by endpoint metadata.
- Use the [ZOOKEEPER_SETUP.md](ZOOKEEPER_SETUP.md) guide for scripted examples of populating the ZooKeeper tree.
