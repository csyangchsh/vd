package com.csyangchsh.demo.vd;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Test program using local Ollama to generate document vectors and insert into VectorDB via HTTP API.
 *
 * Requirements:
 * - VectorDB server running (default: http://localhost:8080)
 * - Ollama running locally (default: http://localhost:11434)
 * - An embedding model pulled (e.g., ollama pull nomic-embed-text)
 *
 * Usage:
 * <pre>
 * # Start VectorDB server
 * java -jar vector-db.jar --config config.yaml
 *
 * # Start Ollama service
 * ollama serve
 *
 * # Pull embedding model
 * ollama pull qwen3-embedding:0.6b
 *
 * # Run this test
 * mvn exec:java -Dexec.mainClass="com.csyangchsh.demo.vd.OllamaEmbeddingTest"
 * </pre>
 */
public class OllamaEmbeddingTest {

    private static final Logger logger = LoggerFactory.getLogger(OllamaEmbeddingTest.class);

    // Ollama configuration
    private static final String OLLAMA_BASE_URL = System.getProperty(
            "ollama.url", "http://localhost:11434");
    private static final String OLLAMA_MODEL = System.getProperty(
            "ollama.model", "qwen3-embedding:0.6b");
    private static final int EMBEDDING_DIMENSION = 128; // qwen3-embedding dimension

    // VectorDB server configuration
    private static final String VECTORDB_URL = System.getProperty(
            "vectordb.url", "http://localhost:8080");

    private final HttpClient httpClient;
    private final Gson gson;

    public OllamaEmbeddingTest() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.gson = new Gson();

        logger.info("Initialized OllamaEmbeddingTest");
        logger.info("VectorDB URL: {}", VECTORDB_URL);
        logger.info("Ollama URL: {}", OLLAMA_BASE_URL);
        logger.info("Ollama Model: {}", OLLAMA_MODEL);
        logger.info("Embedding Dimension: {}", EMBEDDING_DIMENSION);
    }

    /**
     * Generate embedding for a single text using Ollama
     */
    public float[] embed(String text) throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", OLLAMA_MODEL);
        requestBody.addProperty("prompt", text);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_BASE_URL + "/api/embeddings"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama API error: " + response.body());
        }

        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
        JsonArray embedding = jsonResponse.getAsJsonArray("embedding");

        if (embedding == null) {
            throw new RuntimeException("Invalid response from Ollama: " + response.body());
        }

        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = embedding.get(i).getAsFloat();
        }

        return vector;
    }

    /**
     * Check VectorDB server health
     */
    public JsonObject checkVectorDBHealth() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(VECTORDB_URL + "/health"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        return gson.fromJson(response.body(), JsonObject.class);
    }

    /**
     * Get VectorDB server metrics
     */
    public JsonObject getVectorDBMetrics() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(VECTORDB_URL + "/metrics"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        return gson.fromJson(response.body(), JsonObject.class);
    }

    /**
     * Insert document with embedding into VectorDB via HTTP API
     */
    public JsonObject insertDocument(String text, String category, JsonObject metadata) {
        try {
            logger.info("Generating embedding for: {}...",
                    text.substring(0, Math.min(50, text.length())));
            float[] embedding = embed(text);

            // Convert float array to JsonArray
            JsonArray vectorArray = new JsonArray();
            for (float v : embedding) {
                vectorArray.add(v);
            }

            // Build request body
            JsonObject requestBody = new JsonObject();
            requestBody.add("vector", vectorArray);
            requestBody.addProperty("text", text);

            if (metadata == null) {
                metadata = new JsonObject();
            }
            metadata.addProperty("category", category);
            metadata.addProperty("timestamp", System.currentTimeMillis());
            requestBody.add("metadata", metadata);

            // Send HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VECTORDB_URL + "/vectors"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            JsonObject result = gson.fromJson(response.body(), JsonObject.class);
            logger.info("Inserted document with ID: {}", result.get("id").getAsString());
            return result;

        } catch (Exception e) {
            logger.error("Failed to insert document", e);
            throw new RuntimeException("Failed to insert document", e);
        }
    }

    /**
     * Insert multiple documents via HTTP API (batch)
     */
    public JsonObject insertDocumentsBatch(List<Document> documents) {
        try {
            JsonArray vectorsArray = new JsonArray();

            for (Document doc : documents) {
                float[] embedding = embed(doc.text());

                JsonArray vectorArray = new JsonArray();
                for (float v : embedding) {
                    vectorArray.add(v);
                }

                JsonObject vectorObj = new JsonObject();
                vectorObj.add("vector", vectorArray);
                vectorObj.addProperty("text", doc.text());

                JsonObject metadata = doc.metadata();
                if (metadata == null) {
                    metadata = new JsonObject();
                }
                metadata.addProperty("category", doc.category());
                metadata.addProperty("timestamp", System.currentTimeMillis());
                vectorObj.add("metadata", metadata);

                vectorsArray.add(vectorObj);
            }

            JsonObject requestBody = new JsonObject();
            requestBody.add("vectors", vectorsArray);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VECTORDB_URL + "/vectors"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            JsonObject result = gson.fromJson(response.body(), JsonObject.class);
            JsonArray ids = result.getAsJsonArray("ids");
            logger.info("Batch inserted {} documents", ids.size());
            return result;

        } catch (Exception e) {
            logger.error("Failed to batch insert documents", e);
            throw new RuntimeException("Failed to batch insert documents", e);
        }
    }

    /**
     * Check Ollama service health
     */
    public boolean checkOllamaHealth() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_BASE_URL + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
                JsonArray models = jsonResponse.getAsJsonArray("models");
                if (models != null) {
                    logger.info("Ollama is running. Available models:");
                    for (int i = 0; i < models.size(); i++) {
                        JsonObject model = models.get(i).getAsJsonObject();
                        logger.info("  - {}", model.get("name").getAsString());
                    }
                }
                return true;
            }
        } catch (Exception e) {
            logger.warn("Ollama health check failed: {}", e.getMessage());
        }
        return false;
    }

    // ========== Helper Methods ==========

    /**
     * Create metadata JsonObject
     */
    private JsonObject createMetadata(String source, int year) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("source", source);
        metadata.addProperty("year", year);
        return metadata;
    }

    // ========== Test Methods ==========

    /**
     * Test with sample documents
     */
    public void testWithSampleDocuments() {
        logger.info("=== Testing with sample documents ===");

        // Sample documents about various topics
        List<Document> documents = List.of(
                new Document(
                        "Artificial intelligence is transforming industries by automating complex tasks and enabling new capabilities.",
                        "technology",
                        createMetadata("tech-news", 2026)
                ),
                new Document(
                        "Climate change poses significant challenges to global ecosystems and requires immediate action.",
                        "environment",
                        createMetadata("science-journal", 2026)
                ),
                new Document(
                        "Financial markets experienced volatility due to changing economic policies and geopolitical tensions.",
                        "finance",
                        createMetadata("business-news", 2026)
                ),
                new Document(
                        "Machine learning algorithms can analyze vast amounts of data to identify patterns and make predictions.",
                        "technology",
                        createMetadata("ai-research", 2026)
                ),
                new Document(
                        "Renewable energy sources like solar and wind are becoming increasingly cost-effective alternatives to fossil fuels.",
                        "environment",
                        createMetadata("energy-report", 2026)
                ),
                new Document(
                        "Central banks adjust interest rates to control inflation and stabilize economic growth.",
                        "finance",
                        createMetadata("economics-review", 2026)
                )
        );

        // Insert documents in batch
        insertDocumentsBatch(documents);

        printStats();
    }

    /**
     * Get and print database statistics
     */
    public void printStats() {
        try {
            JsonObject metrics = getVectorDBMetrics();
            JsonObject database = metrics.getAsJsonObject("database");

            logger.info("=== VectorDB Statistics ===");
            logger.info("Total vectors: {}", database.get("total_vectors").getAsInt());
            logger.info("Active vectors: {}", database.get("active_vectors").getAsInt());
            logger.info("Memory bytes: {}", database.get("memory_bytes").getAsLong());
        } catch (Exception e) {
            logger.error("Failed to get stats", e);
        }
    }

    // ========== Main ==========

    public static void main(String[] args) {
        OllamaEmbeddingTest test = new OllamaEmbeddingTest();

        try {
            // Check VectorDB server
            logger.info("Checking VectorDB server at: {}", VECTORDB_URL);
            JsonObject health = test.checkVectorDBHealth();
            if (!"HEALTHY".equals(health.get("status").getAsString())) {
                logger.error("VectorDB server is not healthy. Please start the server first.");
                logger.error("Run: java -jar vector-db.jar --config config.yaml");
                return;
            }
            logger.info("VectorDB server is OK");

            // Check Ollama service
            if (!test.checkOllamaHealth()) {
                logger.error("Ollama service is not available. Please start Ollama with: ollama serve");
                logger.error("And pull the embedding model: ollama pull {}", OLLAMA_MODEL);
                return;
            }

            // Run tests
            test.testWithSampleDocuments();

            logger.info("\n=== Test completed successfully ===");

        } catch (Exception e) {
            logger.error("Test failed", e);
        }
    }

    // ========== Helper Classes ==========

    private record Document(String text, String category, JsonObject metadata) {}
}
