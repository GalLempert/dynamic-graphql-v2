# Change Stream Checkpointing

## Overview

Sequence-based pagination relies on MongoDB change streams to deliver incremental updates. The service persists resume tokens per collection so clients can resume consumption even after restarts.

## Components

| Component | Role |
|-----------|------|
| `SequenceQueryRequest` | Represents a sequence read, invoking the repository with the requested start sequence and batch size.【F:src/main/java/iaf/ofek/sigma/dto/request/SequenceQueryRequest.java†L17-L61】 |
| `RequestValidator` | Ensures the endpoint opted into sequence queries and enforces sane bounds on `sequence` and `bulkSize` parameters.【F:src/main/java/iaf/ofek/sigma/service/validation/RequestValidator.java†L39-L71】 |
| `DynamicMongoRepository` | Manages change streams, resume tokens, and response envelopes.【F:src/main/java/iaf/ofek/sigma/persistence/repository/DynamicMongoRepository.java†L115-L211】 |
| `SequenceCheckpoint` entity | Stores the latest processed sequence and resume token for each collection.【F:src/main/java/iaf/ofek/sigma/persistence/entity/SequenceCheckpoint.java†L1-L36】 |
| `SequenceCheckpointRepository` | Spring Data repository used to read/write checkpoint documents.【F:src/main/java/iaf/ofek/sigma/persistence/repository/SequenceCheckpointRepository.java†L1-L20】 |

## Processing Loop

1. **Lookup** – The repository fetches the stored checkpoint for the collection. If the saved sequence matches the client's `startSequence`, the corresponding resume token is used to resume the change stream. Otherwise a fresh stream is opened from the current head.【F:src/main/java/iaf/ofek/sigma/persistence/repository/DynamicMongoRepository.java†L139-L176】
2. **Iteration** – Up to `bulkSize` change events are read, converted into a payload containing operation type, document key, and (when available) the full document contents.【F:src/main/java/iaf/ofek/sigma/persistence/repository/DynamicMongoRepository.java†L177-L204】
3. **Checkpoint** – The last resume token and incremented sequence are persisted to the `_sequence_checkpoints` collection via `SequenceCheckpointRepository.save(...)`, making the next request resumable.【F:src/main/java/iaf/ofek/sigma/persistence/repository/DynamicMongoRepository.java†L205-L239】
4. **Response** – The repository returns a map with `data`, `nextSequence`, and `hasMore`. The orchestrator wraps this in a `SequenceResponse` DTO that clients consume.【F:src/main/java/iaf/ofek/sigma/dto/request/SequenceQueryRequest.java†L43-L61】【F:src/main/java/iaf/ofek/sigma/persistence/repository/DynamicMongoRepository.java†L200-L211】

## Schema

Checkpoint documents follow a simple shape:

```json
{
  "_id": "orders",           // collection name
  "sequence": 1285,           // next sequence the client should request
  "resumeToken": "{...}"      // serialized change stream resume token
}
```

`SequenceCheckpointRepository` addresses documents by collection name, enabling atomic overwrites on each batch.【F:src/main/java/iaf/ofek/sigma/persistence/repository/SequenceCheckpointRepository.java†L1-L20】

## Operational Guidance

- Ensure MongoDB runs as a replica set; change streams and transactions require it.
- Tune `bulkSize` on a per-endpoint basis using the ZooKeeper `defaultBulkSize` property to balance throughput and latency.【F:src/main/java/iaf/ofek/sigma/controller/EndpointRegistry.java†L70-L118】
- Monitor logs for the `Saved checkpoint` message to confirm progress is being persisted between polling cycles.【F:src/main/java/iaf/ofek/sigma/persistence/repository/DynamicMongoRepository.java†L205-L239】
