# Simple Vector DB

A simple high-performance single-machine vector database implemented in Java, designed for learning vector database algorithms and principles.

## Features

- **UUID v7 IDs**: Distributed-friendly, time-ordered identifiers with client-side generation
- **Text & Metadata Storage**: Store original text and structured metadata with vectors
- **Metadata Filtering**: Filter search results by metadata fields (String, Long, Double, Boolean)
- **Write-Ahead Log (WAL)**: Durable operations with crash recovery and automatic checkpointing
- **Index Types**
  - Flat Index: Brute-force exact search (100% recall)
  - Compact Flat Index: Memory-efficient exact search (30-50% less memory)
  - HNSW Index: Hierarchical Navigable Small World for high-performance approximate search
  - PQ Index: Product Quantization for large-scale datasets (8-16x compression)

- **Distance Metrics**
  - L2 Distance (Euclidean)
  - Cosine Similarity
  - Inner Product

- **Performance Optimizations**
  - SIMD-accelerated distance calculations (2-4x faster)
  - Concurrent search with read-write locks
  - Compact vector storage for memory efficiency

- **Data Persistence**: Save and load vector data and indexes to/from disk

- **Payload Support**: Associate arbitrary byte arrays with vectors

- **Standalone Server Mode**: Run as an independent HTTP service

- **Collection Support**: Multiple named collections per database

## Requirements

- Java 25+ (with preview features enabled)
- Maven 3.x

## Building

```bash
mvn clean package
```

## Usage Modes

### 1. Embedded Library Mode

Use VectorDB as a library in your Java application:

```java
import com.csyangchsh.demo.vd.api.VectorDB;
import com.csyangchsh.demo.vd.model.*;

// Create a database with 128-dimensional vectors
VectorDB db = VectorDB.create(128);

// Insert vector with text and metadata
float[] vector1 = new float[128];
// ... fill vector1 ...
Metadata metadata = new Metadata()
    .put("category", "news")
    .put("timestamp", System.currentTimeMillis())
    .put("score", 0.95);
String id1 = db.insert(vector1, "Sample document text", metadata);

// Insert simple vector
String id2 = db.insert(new float[128]);

// Search for similar vectors
float[] query = new float[128];
// ... fill query ...
SearchResult[] results = db.search(query, 10);

// Search with metadata filter
Filter filter = Filter.and(
    Filter.eq("category", "news"),
    Filter.gte("timestamp", 1704067200000L)
);
SearchResult[] filteredResults = db.search(query, 10, filter);

// Get vector by ID
Vector vector = db.get(id1);
System.out.println("Text: " + vector.getText());
System.out.println("Metadata: " + vector.getMetadata());

// Save to disk
db.save("mydb.bin");

// Load from disk
VectorDB loadedDb = VectorDB.load("mydb.bin");
```

#### Using WAL (Write-Ahead Log) for Durability

```java
// Enable WAL with 5-minute checkpoint interval
db.enableWAL("./data", 5 * 60 * 1000);

// All insert/delete operations are now logged to WAL
String id = db.insert(vector, "text", metadata);

// Create checkpoint (saves database and truncates WAL)
db.checkpoint("./data");

// Disable WAL when done
db.disableWAL();
```

### 2. Standalone Server Mode

Run VectorDB as an independent HTTP service:

#### Starting the Server

**Linux/macOS:**
```bash
# Start in foreground
./start-server.sh

# Start in background (daemon mode)
./start-server.sh --daemon

# Check status
./start-server.sh --status

# Stop server
./start-server.sh --stop
```

**Windows:**
```cmd
# Start in foreground
start-server.bat

# Start in background
start-server.bat --daemon

# Check status
start-server.bat --status

# Stop server
start-server.bat --stop
```

**Direct Java:**
```bash
java --enable-preview --add-modules=jdk.incubator.vector \
     -cp target/simple-vector-db-1.0.0.jar \
     com.csyangchsh.demo.vd.server.VectorDBServer \
     --config config.yaml
```

#### Server Configuration

Edit `config.yaml` to customize server settings:

```yaml
server:
  host: "0.0.0.0"      # Bind address
  port: 8080            # HTTP port
  threads: 16           # Worker threads

database:
  dimension: 128        # Vector dimension
  distanceType: "L2"    # Distance metric
  indexType: "HNSW"     # Index type
  indexPath: "./data/vectordb"
  autoSave: true
  autoSaveInterval: 300  # Auto-save every 5 minutes
```

#### REST API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/metrics` | GET | Server metrics |
| `/vectors` | GET | List vector info |
| `/vectors` | POST | Insert vector(s) |
| `/vectors/all` | GET | Get all vectors with details |
| `/vectors` | DELETE | Delete vector(s) |
| `/search` | POST | Search vectors |

**Example API Usage:**

```bash
# Insert a vector
curl -X POST http://localhost:8080/vectors \
  -H "Content-Type: application/json" \
  -d '{
    "vector": [0.1, 0.2, 0.3, 0.4, 0.5]
  }'

# Search vectors
curl -X POST http://localhost:8080/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": [0.1, 0.2, 0.3, 0.4, 0.5],
    "k": 10
  }'

# Get metrics
curl http://localhost:8080/metrics
```

For more details, see [SERVER.md](SERVER.md).

## Running Tests

```bash
mvn test
```

## Ollama Integration Test

Test VectorDB with real document embeddings using local Ollama via HTTP API:

```bash
# Terminal 1: Start VectorDB server
mvn exec:java -Dexec.mainClass="com.csyangchsh.demo.vd.server.VectorDBServer" \
  -Dexec.args="--config config.yaml"

# Terminal 2: Install and start Ollama
ollama serve

# Pull an embedding model
ollama pull qwen3-embedding:0.6b

# Run the Ollama integration test
mvn exec:java -Dexec.mainClass="com.csyangchsh.demo.vd.OllamaEmbeddingTest"
```

The `OllamaEmbeddingTest` class demonstrates:
- Connecting to VectorDB server via HTTP API
- Generating embeddings from text using Ollama's API
- Inserting documents with metadata via HTTP
- Semantic search with metadata filtering
- Saving database via HTTP API

## HTTP API Test

**Using curl directly:**

```bash
# Check health
curl http://localhost:8080/health

# Get metrics
curl http://localhost:8080/metrics

# Insert a vector
curl -X POST http://localhost:8080/vectors \
  -H "Content-Type: application/json" \
  -d '{
    "vector": [0.1, 0.2, 0.3, 0.4, 0.5],
    "text": "Sample document",
    "metadata": {"category": "test", "timestamp": 1704067200000}
  }'

# Search vectors
curl -X POST http://localhost:8080/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": [0.1, 0.2, 0.3, 0.4, 0.5],
    "k": 10
  }'

# Get all vectors
curl http://localhost:8080/vectors/all

# Save database
curl -X POST http://localhost:8080/save \
  -H "Content-Type: application/json" \
  -d '{"path": "./data/mydb.db"}'
```

## Running the Example

## Performance Test

```bash
mvn exec:java -Dexec.mainClass="com.csyangchsh.demo.vd.PerformanceTest"
```

## Using Different Index Types

### Flat Index (Exact Search)
```java
VectorDB db = VectorDB.create(128, DistanceType.L2);
```

### HNSW Index (Approximate, Fast)
```java
VectorDB db = VectorDB.createWithHNSW(128, DistanceType.L2, 16);
```

### Compact Flat Index (Memory Efficient)
```java
VectorDB db = VectorDB.create(128, DistanceType.L2, IndexType.COMPACT_FLAT);
```

### PQ Index (Compression)
```java
VectorDB db = VectorDB.create(128, DistanceType.L2, IndexType.PQ);
```

## Metadata Filtering

VectorDB supports powerful metadata filtering during search:

```java
// Create vectors with metadata
Metadata metadata1 = new Metadata()
    .put("category", "news")
    .put("year", 2024)
    .put("score", 0.95);
db.insert(vector1, "Article 1", metadata1);

// Filter by equality
Filter filter = Filter.eq("category", "news");
SearchResult[] results = db.search(query, 10, filter);

// Filter by range
Filter rangeFilter = Filter.and(
    Filter.eq("category", "news"),
    Filter.gte("year", 2023),
    Filter.gt("score", 0.8)
);

// String operations
Filter stringFilter = Filter.and(
    Filter.contains("title", "breaking"),
    Filter.startsWith("author", "John")
);

// Logical operators
Filter complexFilter = Filter.or(
    Filter.eq("status", "published"),
    Filter.eq("status", "archived")
);
```

## UUID v7 Identifiers

VectorDB uses UUID v7 for all vector IDs:

```java
// Insert returns UUID v7 string
String id = db.insert(vector);

// UUID v7 is time-ordered and sortable
// Format: xxxxxxxx-xxxx-7xxx-xxxx-xxxxxxxxxxxx

// Extract timestamp from UUID v7
long timestamp = UUIDv7.getTimestamp(id);

// Check if a string is valid UUID v7
boolean isValid = UUIDv7.isUUIDv7(id);
```

Benefits:
- **Distributed-friendly**: Client-side generation, no coordination needed
- **Time-ordered**: Sortable by generation time
- **Globally unique**: No collisions across distributed systems

## Project Structure

```
src/main/java/com/csyangchsh/demo/vd/
├── api/              # Public API (VectorDB)
├── core/             # Core engine (VectorCollection)
├── index/            # Index implementations
│   ├── FlatIndex.java
│   ├── CompactFlatIndex.java
│   ├── HNSWIndex.java
│   ├── PQIndex.java
│   ├── Index.java
│   └── VectorIO.java
├── model/            # Data models
│   ├── Vector.java
│   ├── Metadata.java
│   ├── Filter.java
│   ├── SearchRequest.java
│   ├── SearchResult.java
│   ├── DistanceType.java
│   └── IndexType.java
├── storage/          # Persistence layer
│   ├── DBStorage.java
│   ├── CompactVectorStorage.java
│   └── WAL.java
├── server/           # HTTP Server
│   ├── VectorDBServer.java
│   ├── NettyHttpServer.java
│   ├── ServerConfig.java
│   ├── MetricsCollector.java
│   └── model/        # API request/response models
├── util/             # Utility classes
│   ├── DistanceUtil.java
│   ├── SIMDDistanceUtil.java
│   ├── UUIDv7.java
│   └── VectorUtil.java
└── exception/        # Exceptions
    └── VectorDBException.java
```

## Documentation

- [SERVER.md](SERVER.md) - Server mode documentation
- [DESIGN.md](DESIGN.md) - Design document

## Performance

| Index Type | QPS | Recall | Memory |
|------------|-----|--------|--------|
| Flat | ~5K | 100% | Baseline |
| Compact Flat | ~6K | 100% | -40% |
| HNSW (M=16) | ~50K | 95%+ | +60% |
| PQ | ~10K | 95%+ | -90% |

*Performance on 100K 128-dimensional vectors, single-threaded*

## License

MIT License
