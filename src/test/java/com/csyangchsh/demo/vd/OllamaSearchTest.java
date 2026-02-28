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

/**
 * Search test program using local Ollama to generate query embeddings and search VectorDB via HTTP API.
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
 * mvn exec:java -Dexec.mainClass="com.csyangchsh.demo.vd.OllamaSearchTest"
 * </pre>
 */
public class OllamaSearchTest {

    private static final Logger logger = LoggerFactory.getLogger(OllamaSearchTest.class);

    // Ollama configuration
    private static final String OLLAMA_BASE_URL = System.getProperty(
            "ollama.url", "http://localhost:11434");
    private static final String OLLAMA_MODEL = System.getProperty(
            "ollama.model", "qwen3-embedding:0.6b");

    // VectorDB server configuration
    private static final String VECTORDB_URL = System.getProperty(
            "vectordb.url", "http://localhost:8080");

    private final HttpClient httpClient;
    private final Gson gson;

    public OllamaSearchTest() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.gson = new Gson();

        logger.info("Initialized OllamaSearchTest");
        logger.info("VectorDB URL: {}", VECTORDB_URL);
        logger.info("Ollama URL: {}", OLLAMA_BASE_URL);
        logger.info("Ollama Model: {}", OLLAMA_MODEL);
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

    /**
     * Search similar documents via HTTP API
     */
    public JsonObject search(String query, int topK) {
        return search(query, topK, null);
    }

    /**
     * Search similar documents with filter via HTTP API
     */
    public JsonObject search(String query, int topK, JsonObject filter) {
        try {
            logger.info("Searching for: {}", query);
            float[] queryEmbedding = embed(query);

            // Convert query vector to JsonArray
            JsonArray queryArray = new JsonArray();
            for (float v : queryEmbedding) {
                queryArray.add(v);
            }

            // Build request body
            JsonObject requestBody = new JsonObject();
            requestBody.add("query", queryArray);
            requestBody.addProperty("k", topK);

            if (filter != null) {
                requestBody.add("filter", filter);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VECTORDB_URL + "/search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return gson.fromJson(response.body(), JsonObject.class);

        } catch (Exception e) {
            logger.error("Failed to search", e);
            throw new RuntimeException("Failed to search", e);
        }
    }

    // ========== Test Methods ==========

    /**
     * Test search functionality
     */
    public void testSearch() {
        logger.info("=== Testing search functionality ===");

        String[] queries = {
                "AI and machine learning advances",
                "Environmental protection and sustainability",
                "Economic policy and markets"
        };

        for (String query : queries) {
            logger.info("\n--- Query: {} ---", query);
            JsonObject response = search(query, 3);

            JsonArray results = response.getAsJsonArray("results");
            for (int i = 0; i < results.size(); i++) {
                JsonObject result = results.get(i).getAsJsonObject();
                double score = result.get("score").getAsDouble();
                String id = result.get("id").getAsString();
                logger.info("Result {}: score={}, id={}", i + 1, score, id);

                if (result.has("text")) {
                    String text = result.get("text").getAsString();
                    logger.info("  Text: {}", text.substring(0, Math.min(100, text.length())) + "...");
                }
            }
        }
    }

    /**
     * Test search with filter
     */
    public void testSearchWithFilter() {
        logger.info("=== Testing search with filter ===");

        // Create filter for category = technology
        JsonObject filter = new JsonObject();
        filter.addProperty("field", "category");
        filter.addProperty("operation", "EQ");
        filter.addProperty("value", "technology");

        JsonObject response = search("AI advances", 5, filter);

        JsonArray results = response.getAsJsonArray("results");
        logger.info("Found {} results with filter", results.size());

        for (int i = 0; i < results.size(); i++) {
            JsonObject result = results.get(i).getAsJsonObject();
            String id = result.get("id").getAsString();
            double score = result.get("score").getAsDouble();

            logger.info("Result {}: score={}, id={}", i + 1, score, id);
            if (result.has("text")) {
                String text = result.get("text").getAsString();
                logger.info("  Text: {}", text.substring(0, Math.min(100, text.length())) + "...");
            }
        }
    }

    // ========== Main ==========

    public static void main(String[] args) {
        OllamaSearchTest test = new OllamaSearchTest();

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
            test.testSearch();
            test.testSearchWithFilter();

            logger.info("\n=== Test completed successfully ===");

        } catch (Exception e) {
            logger.error("Test failed", e);
        }
    }
}
