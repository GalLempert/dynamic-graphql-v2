# Dynamic Enum Management

## Overview

Dynamic enums decouple schema enumerations from application deployments. Enum definitions are sourced from an external service, cached locally, injected into JSON Schemas, and used to enrich API responses with human-readable literals.

## Data Flow

1. **Enum discovery** – `EnumRegistry` periodically fetches definitions from the external service configured via ZooKeeper (`dataSource/enumURL`).【F:src/main/java/iaf/ofek/sigma/service/enums/EnumRegistry.java†L24-L79】【F:src/main/java/iaf/ofek/sigma/service/enums/EnumServiceClient.java†L20-L64】
2. **Schema augmentation** – When schemas load, `EnumSchemaAugmentor` replaces `"enumRef": "statusCodes"` placeholders with concrete `enum` arrays and records bindings for downstream response enrichment.【F:src/main/java/iaf/ofek/sigma/service/schema/EnumSchemaAugmentor.java†L17-L56】
3. **Response transformation** – After a read operation, `EnumResponseTransformer` looks up the bound enum fields and swaps raw codes with `{ "code": "...", "value": "..." }` structures for each occurrence in the payload.【F:src/main/java/iaf/ofek/sigma/service/enums/EnumResponseTransformer.java†L25-L139】
4. **Change propagation** – When enums are refreshed, the registry notifies `SchemaManager` via the `EnumRegistryListener` interface so cached schemas are evicted and rebuilt with the new catalog.【F:src/main/java/iaf/ofek/sigma/service/schema/SchemaManager.java†L23-L114】

## Configuration

| Node | Purpose |
|------|---------|
| `/dataSource/enumURL` | Base URL for the external enum service. When missing, the registry disables enum usage gracefully.【F:src/main/java/iaf/ofek/sigma/config/properties/ZookeeperConfigProperties.java†L78-L121】【F:src/main/java/iaf/ofek/sigma/service/enums/EnumRegistry.java†L60-L78】 |
| `/Globals/EnumRefreshIntervalSeconds` | Controls how frequently the scheduled reload job runs. Parsed by `EnumSchedulerProperties`.【F:src/main/java/iaf/ofek/sigma/service/enums/EnumSchedulerProperties.java†L1-L40】 |
| `/Globals/FailOnEnumLoadFailure` | Optional guard that throws on refresh errors to prevent serving partially configured endpoints.【F:src/main/java/iaf/ofek/sigma/service/enums/EnumRegistry.java†L32-L75】 |

In addition, schemas reference enums using the `enumRef` property:

```json
{
  "type": "object",
  "properties": {
    "status": {
      "type": "string",
      "enumRef": "orderStatuses"
    }
  }
}
```

## Error Handling

- Refresh failures log the exception and either raise (when fail-fast is enabled) or retain the previous catalog.
- If a schema references an unknown enum, schema loading fails with a descriptive message, preventing partially configured endpoints from accepting writes.【F:src/main/java/iaf/ofek/sigma/service/schema/EnumSchemaAugmentor.java†L43-L52】
- Response transformation skips enrichment when the enum registry lacks a matching definition, ensuring reads do not crash even if an enum was removed unexpectedly.【F:src/main/java/iaf/ofek/sigma/service/enums/EnumResponseTransformer.java†L119-L139】

## Operational Tips

- Schedule refresh intervals conservatively (e.g. 60 seconds) to balance load on the enum service with update latency.
- Use ZooKeeper ACLs to protect the `dataSource` and `Globals` nodes; incorrect URLs or toggles can disable enum enrichment across the fleet.
- Pair this guide with [CONFIGURATION_BASED_VALIDATION.md](CONFIGURATION_BASED_VALIDATION.md) to see how schema augmentation feeds into request validation.
