# Kestra Qdrant Plugin Specification

**Plugin Identifier**: `io.kestra.plugin.qdrant`  
**Plugin Artifact**: `io.kestra.plugin:plugin-qdrant:1.0.0-SNAPSHOT`  
**Underlying SDK**: `io.qdrant:client:1.19.0` (Official Qdrant Java gRPC Client)  
**Supported Server Versions**: Qdrant Server `v1.19.x`, modern `v1.18+` (with dense vector oneof support), and **Qdrant Cloud**  
**Transport Protocol**: gRPC (default port `6334`)  

---

## Table of Contents
1. [Architecture & Common Connection](#1-architecture--common-connection)
2. [Task Catalog & Specifications](#2-task-catalog--specifications)
   - [2.1 CreateCollection](#21-createcollection)
   - [2.2 DeleteCollection](#22-deletecollection)
   - [2.3 CollectionInfo](#23-collectioninfo)
   - [2.4 Upsert](#24-upsert)
   - [2.5 Get](#25-get)
   - [2.6 Query (Vector Similarity Search)](#26-query-vector-similarity-search)
   - [2.7 Delete](#27-delete)
3. [Point ID Handling & Resolution Precedence](#3-point-id-handling--resolution-precedence)
4. [Payload Structure & Supported Data Types](#4-payload-structure--supported-data-types)
5. [Filter DSL Specification & Reserved Keywords](#5-filter-dsl-specification--reserved-keywords)
6. [Write Consistency (`wait = true`)](#6-write-consistency-wait--true)
7. [Output & FetchType Conventions](#7-output--fetchtype-conventions)
8. [Complete End-to-End Workflow Example](#8-complete-end-to-end-workflow-example)

---

## 1. Architecture & Common Connection

All tasks inherit from the abstract base class `io.kestra.plugin.qdrant.QdrantConnection`. Every task can be configured with the following connection properties:

| Property | Type | Default | Validation / Constraint | Group | Description |
|---|---|---|---|---|---|
| `host` | `Property<String>` | *Required* | Non-null | `connection` | Hostname or IP of the Qdrant instance (e.g. `localhost`, `127.0.0.1`, or `xyz.cloud.qdrant.io`). |
| `port` | `Property<Integer>` | `6334` | `@Min(1) @Max(65535)` | `connection` | gRPC port. Note: Qdrant REST uses 6333, but this SDK communicates exclusively via gRPC (port `6334`). |
| `apiKey` | `Property<String>` | `null` | `@PluginProperty(secret = true)` + `@ToString.Exclude` | `connection` | Optional API key for authentication. Required when connecting to Qdrant Cloud. Never leaked in logs or execution contexts. |
| `tlsEnabled` | `Property<Boolean>` | `false` | Boolean | `connection` | Set to `true` to enable TLS encryption. Required for Qdrant Cloud (HTTPS/gRPC with TLS). |

### Resource Lifecycle
The gRPC client is constructed using `io.qdrant.client.QdrantClient` and is automatically closed via `try-with-resources` upon task completion. No background channels, thread pools, or file descriptors linger after task execution.

---

## 2. Task Catalog & Specifications

### 2.1 CreateCollection
Creates a new vector collection with specified vector dimension size and distance metric.

* **Class**: `io.kestra.plugin.qdrant.CreateCollection`
* **Category**: `AI`, `DATA`

#### Properties

| Property | Type | Default | Constraints | Description |
|---|---|---|---|---|
| `collectionName` | `Property<String>` | *Required* | `@NotNull` | Name of the collection to create. |
| `vectorSize` | `Property<Integer>` | *Required* | `@NotNull @Min(1)` | Embedding vector dimension (e.g. `1536` for OpenAI `text-embedding-3-small`, `384` for all-MiniLM-L6-v2). |
| `distance` | `Property<Distance>` | `COSINE` | Enum | Distance metric: `COSINE`, `DOT`, `EUCLID`, or `MANHATTAN`. |
| `onDiskPayload` | `Property<Boolean>` | `false` | `@Deprecated` | Store payload metadata on disk instead of in RAM. *Note: Deprecated in Qdrant 1.19 in favor of payload storage memory configuration (`Cold`/`Cached`).* |

#### Output
* `io.kestra.core.models.tasks.VoidOutput` (indicates successful creation without payload).

#### YAML Example
```yaml
id: create_collection
type: io.kestra.plugin.qdrant.CreateCollection
host: "localhost"
port: 6334
collectionName: "products"
vectorSize: 1536
distance: COSINE
```

---

### 2.2 DeleteCollection
Permanently drops a collection, deleting all vectors, payloads, and index structures.

* **Class**: `io.kestra.plugin.qdrant.DeleteCollection`

#### Properties

| Property | Type | Default | Constraints | Description |
|---|---|---|---|---|
| `collectionName` | `Property<String>` | *Required* | `@NotNull` | Name of the collection to delete. |

#### Output
* `io.kestra.core.models.tasks.VoidOutput`

---

### 2.3 CollectionInfo
Retrieves live statistics and configuration of a collection.

* **Class**: `io.kestra.plugin.qdrant.CollectionInfo`

#### Properties

| Property | Type | Default | Constraints | Description |
|---|---|---|---|---|
| `collectionName` | `Property<String>` | *Required* | `@NotNull` | Name of the collection to inspect. |

#### Output Schema (`CollectionInfo.Output`)

| Field | Type | Description |
|---|---|---|
| `status` | `String` | Operational status of the collection: `Green` (healthy), `Yellow` (optimizing/degraded), `Red` (error/unavailable), or `Grey` (optimization pending). |
| `pointsCount` | `Long` | **Approximate** total number of points stored in the collection. |
| `indexedVectorsCount` | `Long` | **Approximate** number of vectors indexed for approximate nearest neighbor search. |
| `vectorSize` | `Long` | Vector dimension size configured for the collection. |
| `distance` | `String` | Configured distance metric (e.g., `Cosine`, `Dot`, `Euclid`). |

---

### 2.4 Upsert
Streams and batch-inserts or updates points with vector embeddings and JSON payload metadata.

* **Class**: `io.kestra.plugin.qdrant.Upsert`

#### Properties

| Property | Type | Default | Constraints | Description |
|---|---|---|---|---|
| `collectionName` | `Property<String>` | *Required* | `@NotNull` | Name of the target collection. |
| `data` | `Data` | *Required* | `@NotNull` | Point data source. Can be an **inline list of maps** or a **storage URI** (`kestra://...`) generated by an upstream task. |
| `batchSize` | `Property<Integer>` | `250` | `@Min(1) @Max(10000)` | Number of points to stream and send per gRPC batch request (bounded to 10,000 to protect against gRPC request frame size exhaustion: $10{,}000 \times 1536 \times 4\text{ bytes} \approx 61.4\text{ MB}$). |

#### Point Structure Accepted by `data`
```json
{
  "id": 1001,
  "vector": [0.05, 0.61, 0.76, 0.74],
  "payload": {
    "title": "Ergonomic Office Chair",
    "category": "furniture",
    "price": 249.99,
    "in_stock": true
  }
}
```

* **`id`** *(optional)*: Point identifier. See [Section 3](#3-point-id-handling--resolution-precedence) for strict resolution precedence.
* **`vector`** or **`vectors`** *(required)*:
  - **Dense vector**: Flat array of numbers: `[0.05, 0.61, 0.76, 0.74]`.
  - **Named vectors map**: Map of vector arrays: `{"image": [0.1, 0.2], "text": [0.3, 0.4]}`.
* **`payload`** *(optional)*: Map containing arbitrary metadata.

---

### 2.5 Get
Retrieves specific points by their IDs.

* **Class**: `io.kestra.plugin.qdrant.Get`

#### Properties

| Property | Type | Default | Constraints | Description |
|---|---|---|---|---|
| `collectionName` | `Property<String>` | *Required* | `@NotNull` | Collection from which to retrieve points. |
| `ids` | `Property<List<Object>>` | *Required* | `@NotNull` | List of point IDs (integers, strings, or UUIDs) to retrieve. |
| `withPayload` | `Property<Boolean>` | `true` | Boolean | Whether to include point metadata payload in the output. |
| `withVectors` | `Property<Boolean>` | `false` | Boolean | Whether to include vector embeddings in the output. |
| `fetchType` | `Property<FetchType>` | `STORE` | Enum | Output mode: `STORE` (writes to `.ion` storage URI), `FETCH` (all rows in memory), `FETCH_ONE` (first row). |

#### Output Structure
Vectors are returned from `VectorOutput.getDense().getDataList()` (with fallback to `getDataList()`), safeguarding against protobuf version deprecations in Qdrant 1.19.

---

### 2.6 Query (Vector Similarity Search)
Executes approximate nearest neighbor (ANN) vector search.

* **Class**: `io.kestra.plugin.qdrant.Query`

#### Properties

| Property | Type | Default | Constraints | Description |
|---|---|---|---|---|
| `collectionName` | `Property<String>` | *Required* | `@NotNull` | Collection to search. |
| `vector` | `Property<List<Object>>` | `null` | Mutually exclusive with `vectorId` | Direct query vector embedding (list of floats). |
| `vectorId` | `Property<Object>` | `null` | Mutually exclusive with `vector` | Query by existing point ID. |
| `topK` | `Property<Integer>` | `10` | `@NotNull @Min(1)` | Maximum number of nearest neighbors to return. |
| `scoreThreshold` | `Property<Float>` | `null` | Optional | Minimum similarity score cutoff. |
| `filter` | `Property<Map<String, Object>>` | `null` | Optional | Metadata filter criteria (see [Section 5](#5-filter-dsl-specification--reserved-keywords)). |
| `withPayload` | `Property<Boolean>` | `true` | Boolean | Whether to return point payload metadata. |
| `withVectors` | `Property<Boolean>` | `false` | Boolean | Whether to return vector values in results. |
| `fetchType` | `Property<FetchType>` | `STORE` | Enum | Output mode: `STORE`, `FETCH`, or `FETCH_ONE`. |

---

### 2.7 Delete
Deletes points from a collection by specific IDs or matching filter criteria.

* **Class**: `io.kestra.plugin.qdrant.Delete`

#### Properties

| Property | Type | Default | Constraints | Description |
|---|---|---|---|---|
| `collectionName` | `Property<String>` | *Required* | `@NotNull` | Target collection. |
| `ids` | `Property<List<Object>>` | `null` | Mutually exclusive with `filter` | List of point IDs (integers, strings, or UUIDs) to delete. |
| `filter` | `Property<Map<String, Object>>` | `null` | Mutually exclusive with `ids` | Metadata filter query to delete all matching points. |

---

## 3. Point ID Handling & Resolution Precedence

In Qdrant, point IDs are strictly either an **unsigned 64-bit integer** (`uint64`) or an **RFC 4122 UUID**. Negative numbers are disallowed by the protocol.

To guarantee that point IDs remain 100% reach-consistent across `Upsert`, `Get`, `Delete`, and `Query.vectorId`, the plugin applies the following strict resolution precedence:

1. **Numeric Objects (`Number`)**:
   - Must be $\ge 0$. Any negative number throws `IllegalArgumentException`.
   - Converted to `PointId.num` (`uint64`).
2. **Java `UUID` Objects**:
   - Converted directly to `PointId.uuid`.
3. **String Values (`str`)**:
   - **Step 3a**: If `Long.parseLong(str)` succeeds with $\text{value} \ge 0$, it is coerced to `PointId.num` (e.g. `"1001"` $\rightarrow$ `1001L`).
   - **Step 3b**: If parseable as a standard 36-character UUID string (e.g. `"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"`), it is coerced to `PointId.uuid`.
   - **Step 3c**: Any other arbitrary string is deterministically converted into an RFC 4122 v3 Name-UUID via `UUID.nameUUIDFromBytes(str.getBytes(StandardCharsets.UTF_8))` and stored as `PointId.uuid`.

---

## 4. Payload Structure & Supported Data Types

Payloads are converted to Qdrant's `JsonWithInt.Value` protobuf representation:

| Java / JSON Type | Qdrant Protobuf Representation | Example |
|---|---|---|
| `String` | `STRING_VALUE` | `"category": "electronics"` |
| `Integer`, `Long`, `Short`, `Byte` | `INTEGER_VALUE` (64-bit signed int) | `"quantity": 50`, `"year": 2026` |
| `Double`, `Float` | `DOUBLE_VALUE` (64-bit float) | `"price": 19.99`, `"rating": 4.8` |
| `Boolean` | `BOOL_VALUE` | `"active": true` |
| `null` | `NULL_VALUE` | `"discount": null` |
| `List<?>` | `LIST_VALUE` | `"tags": ["ai", "database", "kestra"]` |
| `Map<String, ?>` | `STRUCT_VALUE` | `"metadata": {"author": "John", "rev": 2}` |

---

## 5. Filter DSL Specification & Reserved Keywords

### Reserved Operator Keywords
The following top-level keys are reserved for query grouping and operators:
* `range`: Grouping block for numeric range conditions.
* `must`: Logical AND list.
* `should`: Logical OR list.
* `must_not`: Logical NOT list.
* `has_id` / `id`: Match specific point IDs.

*(Note: If a payload field is literally named `must` or `range`, specify conditions using the explicit `{ "key": "range", "match": { "value": ... } }` syntax).*

### Supported Filter Conditions

#### 1. Equality & Keyword Match
```yaml
filter:
  category: "books"
  in_stock: true
  author_id: 42
```

#### 2. Multi-Value Match (IN Operator)
```yaml
filter:
  category: ["fiction", "biography", "history"]
  user_id: [101, 102, 103]
```

#### 3. Numeric Range Queries
Can be defined either nested under `range` or directly on the field:
```yaml
filter:
  price:
    gte: 10.0
    lte: 99.99
```
Or:
```yaml
filter:
  range:
    price:
      gte: 10.0
      lte: 99.99
    age:
      gt: 18
      lt: 65
```

#### 4. Full-Text Search
```yaml
filter:
  description:
    text: "ergonomic office"
```

#### 5. Is Empty / Is Null
```yaml
filter:
  tags:
    is_empty: true
  deleted_at:
    is_null: true
```

#### 6. Has ID
```yaml
filter:
  has_id: [1, 2, "my-doc-uuid"]
```

---

## 6. Write Consistency (`wait = true`)

In Qdrant, write requests (`UpsertPoints` and `DeletePoints`) can return before points are indexed if `wait=false` (UpdateStatus: `Acknowledged`).

**Guarantee**: All writes initiated by `Upsert` and `Delete` execute with **`wait = true`** (hardcoded in the SDK and verified in the plugin). This guarantees that writes are committed and searchable (`UpdateStatus: Completed`) before the task returns, preventing race conditions in sequential pipeline flows.

---

## 7. Output & FetchType Conventions

Tasks that retrieve points (`Get` and `Query`) implement Kestra's standard `fetchType` pattern:

| `fetchType` | Behavior | Memory Footprint | Recommended Use Case | Output Fields Populated |
|---|---|---|---|---|
| **`STORE`** *(Default)* | Serializes all result rows to an Amazon Ion file (`.ion`) in Kestra's internal storage. | Minimal (disk streaming) | Production RAG pipelines passed to downstream LLM/embedding tasks. | `uri`, `size` |
| **`FETCH`** | Returns all retrieved rows as a Java `List<Map<String, Object>>` directly in the execution context. | Proportional to result size | Small to medium result sets (`topK <= 100`) accessed immediately in expressions (`{{ outputs.task_id.rows }}`). | `rows`, `size` |
| **`FETCH_ONE`** | Returns only the first matching row as a single `Map<String, Object>`. | Single object | Fetching a specific point or top-1 nearest neighbor. | `row`, `size` |

---

## 8. Complete End-to-End Workflow Example

```yaml
id: qdrant_rag_pipeline
namespace: company.ai

tasks:
  - id: create_collection
    type: io.kestra.plugin.qdrant.CreateCollection
    host: "localhost"
    port: 6334
    collectionName: "knowledge_base"
    vectorSize: 4
    distance: COSINE

  - id: upsert_embeddings
    type: io.kestra.plugin.qdrant.Upsert
    host: "localhost"
    port: 6334
    collectionName: "knowledge_base"
    batchSize: 100
    data:
      - id: 1
        vector: [0.05, 0.61, 0.76, 0.74]
        payload:
          doc_id: "DOC-001"
          topic: "vector databases"
          content: "Qdrant is an open-source vector search engine."
      - id: 2
        vector: [0.19, 0.81, 0.75, 0.11]
        payload:
          doc_id: "DOC-002"
          topic: "orchestration"
          content: "Kestra orchestrates data and AI pipelines declaratively."

  - id: search_similar
    type: io.kestra.plugin.qdrant.Query
    host: "localhost"
    port: 6334
    collectionName: "knowledge_base"
    vector: [0.05, 0.60, 0.75, 0.73]
    topK: 2
    filter:
      topic: "vector databases"
    fetchType: FETCH

  - id: log_result
    type: io.kestra.plugin.core.log.Log
    message: "Found {{ outputs.search_similar.size }} results. Top score: {{ outputs.search_similar.rows[0].score }}"
```
