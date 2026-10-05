# Qdrant Plugin

Use the Qdrant plugin to manage vector collections, upsert embeddings, retrieve points, query by vector similarity, and delete points in [Qdrant](https://qdrant.tech/), an open-source vector similarity search engine.

## Connection

All tasks inherit common connection properties:
- `host`: The hostname or IP address of the Qdrant gRPC endpoint (required).
- `port`: The gRPC port (default: `6334`).
- `apiKey`: The API key for authentication with Qdrant Cloud or secure self-hosted instances (optional, secret).
- `tlsEnabled`: Whether to connect using TLS/SSL encryption (default: `false`).

## Tasks

- `CreateCollection`: Creates a new vector collection configured with vector dimension size (`vectorSize`), distance metric (`COSINE`, `DOT`, `EUCLID`, or `MANHATTAN`), and optional on-disk payload storage.
- `DeleteCollection`: Drops a collection and removes all stored vectors and payloads.
- `CollectionInfo`: Retrieves details and statistics about a collection, such as total points count, indexed vector count, and distance metric.
- `Upsert`: Upserts points with vector embeddings and metadata payloads. Supports streaming batch processing from inline lists or Kestra storage URIs (`kestra://`).
- `Get`: Retrieves points by their IDs with options to include payloads and vector embeddings. Outputs results inline (`FETCH`), as a single record (`FETCH_ONE`), or to Kestra internal storage (`STORE`).
- `Query`: Performs vector similarity searches using either a raw embedding vector or an existing point ID. Supports payload filters, score thresholds, and fetch output modes (`FETCH`, `FETCH_ONE`, `STORE`).
- `Delete`: Deletes points by specific point IDs or matching filter criteria (mutually exclusive).
