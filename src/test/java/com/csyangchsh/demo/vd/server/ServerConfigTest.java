package com.csyangchsh.demo.vd.server;

import com.csyangchsh.demo.vd.model.DistanceType;
import com.csyangchsh.demo.vd.model.IndexType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ServerConfig.
 *
 * These tests verify:
 * 1. YAML parsing - correct extraction of all configuration values
 * 2. Default values - proper handling of missing optional fields
 * 3. Validation - correct types and ranges
 * 4. Edge cases - minimal config, malformed config
 */
@DisplayName("Server Configuration Tests")
class ServerConfigTest {

    // ========== Server Settings Tests ==========

    @Test
    @DisplayName("Should parse server settings correctly")
    void testParseServerSettings() {
        String yaml = """
            server:
              host: "127.0.0.1"
              port: 9090
              threads: 32
            database:
              dimension: 256
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals("127.0.0.1", serverConfig.getServer().getHost());
        assertEquals(9090, serverConfig.getServer().getPort());
        assertEquals(32, serverConfig.getServer().getThreads());
    }

    @Test
    @DisplayName("Should use default server settings when not specified")
    void testDefaultServerSettings() {
        String yaml = """
            database:
              dimension: 128
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals("0.0.0.0", serverConfig.getServer().getHost());
        assertEquals(8080, serverConfig.getServer().getPort());
        assertEquals(16, serverConfig.getServer().getThreads());
    }

    @Test
    @DisplayName("Should parse server host as string")
    void testServerHostParsing() {
        String yaml = """
            server:
              host: "localhost"
            database:
              dimension: 128
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals("localhost", serverConfig.getServer().getHost());
    }

    @Test
    @DisplayName("Should parse server port as integer")
    void testServerPortParsing() {
        String yaml = """
            server:
              port: 8888
            database:
              dimension: 128
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(8888, serverConfig.getServer().getPort());
    }

    @Test
    @DisplayName("Should parse server threads as integer")
    void testServerThreadsParsing() {
        String yaml = """
            server:
              threads: 8
            database:
              dimension: 128
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(8, serverConfig.getServer().getThreads());
    }

    // ========== Database Settings Tests ==========

    @Test
    @DisplayName("Should parse database settings correctly")
    void testParseDatabaseSettings() {
        String yaml = """
            database:
              dimension: 256
              distanceType: "COSINE"
              indexType: "PQ"
              indexPath: "/custom/path"
              autoSave: false
              autoSaveInterval: 120
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(256, serverConfig.getDatabase().getDimension());
        assertEquals(DistanceType.COSINE, serverConfig.getDatabase().getDistanceType());
        assertEquals(IndexType.PQ, serverConfig.getDatabase().getIndexType());
        assertEquals("/custom/path", serverConfig.getDatabase().getIndexPath().toString());
        assertFalse(serverConfig.getDatabase().isAutoSave());
        assertEquals(120, serverConfig.getDatabase().getAutoSaveInterval());
    }

    @Test
    @DisplayName("Should use default database settings when not specified")
    void testDefaultDatabaseSettings() {
        String yaml = """
            database:
              dimension: 128
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(DistanceType.L2, serverConfig.getDatabase().getDistanceType());
        assertEquals(IndexType.HNSW, serverConfig.getDatabase().getIndexType());
        assertEquals("./data/index", serverConfig.getDatabase().getIndexPath().toString());
        assertTrue(serverConfig.getDatabase().isAutoSave());
        assertEquals(60, serverConfig.getDatabase().getAutoSaveInterval());
    }

    @Test
    @DisplayName("Should parse L2 distance type")
    void testL2DistanceType() {
        String yaml = """
            database:
              dimension: 128
              distanceType: "L2"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(DistanceType.L2, serverConfig.getDatabase().getDistanceType());
    }

    @Test
    @DisplayName("Should parse COSINE distance type")
    void testCosineDistanceType() {
        String yaml = """
            database:
              dimension: 128
              distanceType: "COSINE"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(DistanceType.COSINE, serverConfig.getDatabase().getDistanceType());
    }

    @Test
    @DisplayName("Should parse INNER_PRODUCT distance type")
    void testInnerProductDistanceType() {
        String yaml = """
            database:
              dimension: 128
              distanceType: "INNER_PRODUCT"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(DistanceType.INNER_PRODUCT, serverConfig.getDatabase().getDistanceType());
    }

    @Test
    @DisplayName("Should parse all index types")
    void testAllIndexTypes() {
        String[] indexTypes = {"FLAT", "COMPACT_FLAT", "HNSW", "PQ"};
        IndexType[] expectedTypes = {
            IndexType.FLAT,
            IndexType.COMPACT_FLAT,
            IndexType.HNSW,
            IndexType.PQ
        };

        for (int i = 0; i < indexTypes.length; i++) {
            String yaml = String.format("""
                database:
                  dimension: 128
                  indexType: "%s"
                """, indexTypes[i]);

            Map<String, Object> config = new Yaml().load(yaml);
            ServerConfig serverConfig = new ServerConfig(config);

            assertEquals(expectedTypes[i], serverConfig.getDatabase().getIndexType());
        }
    }

    @Test
    @DisplayName("Should parse index path correctly")
    void testIndexPathParsing() {
        String yaml = """
            database:
              dimension: 128
              indexPath: "/var/lib/vectordb/data"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals("/var/lib/vectordb/data", serverConfig.getDatabase().getIndexPath().toString());
    }

    @Test
    @DisplayName("Should parse relative index path")
    void testRelativeIndexPath() {
        String yaml = """
            database:
              dimension: 128
              indexPath: "./data/vectors"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals("./data/vectors", serverConfig.getDatabase().getIndexPath().toString());
    }

    @Test
    @DisplayName("Should parse autoSave boolean")
    void testAutoSaveBoolean() {
        // Test true
        String yamlTrue = """
            database:
              dimension: 128
              autoSave: true
            """;
        Map<String, Object> configTrue = new Yaml().load(yamlTrue);
        ServerConfig serverConfigTrue = new ServerConfig(configTrue);
        assertTrue(serverConfigTrue.getDatabase().isAutoSave());

        // Test false
        String yamlFalse = """
            database:
              dimension: 128
              autoSave: false
            """;
        Map<String, Object> configFalse = new Yaml().load(yamlFalse);
        ServerConfig serverConfigFalse = new ServerConfig(configFalse);
        assertFalse(serverConfigFalse.getDatabase().isAutoSave());
    }

    @Test
    @DisplayName("Should parse autoSaveInterval integer")
    void testAutoSaveIntervalParsing() {
        String yaml = """
            database:
              dimension: 128
              autoSaveInterval: 300
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(300, serverConfig.getDatabase().getAutoSaveInterval());
    }

    // ========== HNSW Settings Tests ==========

    @Test
    @DisplayName("Should parse HNSW settings when present")
    void testParseHNSWSettings() {
        String yaml = """
            database:
              dimension: 128
            hnsw:
              M: 32
              efConstruction: 400
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertNotNull(serverConfig.getHnsw());
        assertEquals(32, serverConfig.getHnsw().getM());
        assertEquals(400, serverConfig.getHnsw().getEfConstruction());
    }

    @Test
    @DisplayName("Should return null for HNSW settings when not present")
    void testHNSWSettingsNotPresent() {
        String yaml = """
            database:
              dimension: 128
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertNull(serverConfig.getHnsw());
    }

    @Test
    @DisplayName("Should use default HNSW settings when values not specified")
    void testDefaultHNSWSettings() {
        String yaml = """
            database:
              dimension: 128
            hnsw:
              M: 16
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(16, serverConfig.getHnsw().getM());
        assertEquals(200, serverConfig.getHnsw().getEfConstruction()); // Default
    }

    // ========== Logging Settings Tests ==========

    @Test
    @DisplayName("Should parse logging settings when present")
    void testParseLoggingSettings() {
        String yaml = """
            database:
              dimension: 128
            logging:
              level: "DEBUG"
              file: "/var/log/vectordb.log"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertNotNull(serverConfig.getLogging());
        assertEquals("DEBUG", serverConfig.getLogging().getLevel());
        assertEquals("/var/log/vectordb.log", serverConfig.getLogging().getFile());
    }

    @Test
    @DisplayName("Should use default logging settings when not present")
    void testDefaultLoggingSettings() {
        String yaml = """
            database:
              dimension: 128
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertNotNull(serverConfig.getLogging());
        assertEquals("INFO", serverConfig.getLogging().getLevel());
        assertNull(serverConfig.getLogging().getFile());
    }

    @Test
    @DisplayName("Should parse all log levels")
    void testAllLogLevels() {
        String[] levels = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"};

        for (String level : levels) {
            String yaml = String.format("""
                database:
                  dimension: 128
                logging:
                  level: "%s"
                """, level);

            Map<String, Object> config = new Yaml().load(yaml);
            ServerConfig serverConfig = new ServerConfig(config);

            assertEquals(level, serverConfig.getLogging().getLevel());
        }
    }

    @Test
    @DisplayName("Should handle null log file")
    void testNullLogFile() {
        String yaml = """
            database:
              dimension: 128
            logging:
              level: "INFO"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertNull(serverConfig.getLogging().getFile());
    }

    // ========== Full Config Tests ==========

    @Test
    @DisplayName("Should parse complete configuration file")
    void testParseCompleteConfig() {
        String yaml = """
            server:
              host: "0.0.0.0"
              port: 8080
              threads: 16

            database:
              dimension: 128
              distanceType: "L2"
              indexType: "HNSW"
              indexPath: "./data/vectordb"
              autoSave: true
              autoSaveInterval: 60

            hnsw:
              M: 16
              efConstruction: 200

            logging:
              level: "INFO"
              file: "/var/log/vectordb/server.log"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        // Server
        assertEquals("0.0.0.0", serverConfig.getServer().getHost());
        assertEquals(8080, serverConfig.getServer().getPort());
        assertEquals(16, serverConfig.getServer().getThreads());

        // Database
        assertEquals(128, serverConfig.getDatabase().getDimension());
        assertEquals(DistanceType.L2, serverConfig.getDatabase().getDistanceType());
        assertEquals(IndexType.HNSW, serverConfig.getDatabase().getIndexType());
        assertEquals("./data/vectordb", serverConfig.getDatabase().getIndexPath().toString());
        assertTrue(serverConfig.getDatabase().isAutoSave());
        assertEquals(60, serverConfig.getDatabase().getAutoSaveInterval());

        // HNSW
        assertNotNull(serverConfig.getHnsw());
        assertEquals(16, serverConfig.getHnsw().getM());
        assertEquals(200, serverConfig.getHnsw().getEfConstruction());

        // Logging
        assertEquals("INFO", serverConfig.getLogging().getLevel());
        assertEquals("/var/log/vectordb/server.log", serverConfig.getLogging().getFile());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle numeric port as string")
    void testNumericPortAsString() {
        String yaml = """
            server:
              port: "9090"
            database:
              dimension: 128
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(9090, serverConfig.getServer().getPort());
    }

    @Test
    @DisplayName("Should handle numeric threads as string")
    void testNumericThreadsAsString() {
        String yaml = """
            server:
              threads: "8"
            database:
              dimension: 128
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(8, serverConfig.getServer().getThreads());
    }

    @Test
    @DisplayName("Should handle numeric dimension as string")
    void testNumericDimensionAsString() {
        String yaml = """
            database:
              dimension: "256"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(256, serverConfig.getDatabase().getDimension());
    }

    @Test
    @DisplayName("Should handle numeric autoSaveInterval as string")
    void testNumericAutoSaveIntervalAsString() {
        String yaml = """
            database:
              dimension: 128
              autoSaveInterval: "120"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(120, serverConfig.getDatabase().getAutoSaveInterval());
    }

    @Test
    @DisplayName("Should handle numeric HNSW parameters as strings")
    void testNumericHNSWParamsAsString() {
        String yaml = """
            database:
              dimension: 128
            hnsw:
              M: "32"
              efConstruction: "400"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(32, serverConfig.getHnsw().getM());
        assertEquals(400, serverConfig.getHnsw().getEfConstruction());
    }

    @Test
    @DisplayName("Should handle boolean autoSave as string")
    void testBooleanAutoSaveAsString() {
        String yaml = """
            database:
              dimension: 128
              autoSave: "false"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertFalse(serverConfig.getDatabase().isAutoSave());
    }

    @Test
    @DisplayName("Should handle different boolean representations")
    void testBooleanRepresentations() {
        // Test with boolean true
        String yamlTrue = """
            database:
              dimension: 128
              autoSave: true
            """;
        Map<String, Object> configTrue = new Yaml().load(yamlTrue);
        ServerConfig serverConfigTrue = new ServerConfig(configTrue);
        assertTrue(serverConfigTrue.getDatabase().isAutoSave());

        // Test with Boolean.FALSE
        String yamlFalse = """
            database:
              dimension: 128
              autoSave: false
            """;
        Map<String, Object> configFalse = new Yaml().load(yamlFalse);
        ServerConfig serverConfigFalse = new ServerConfig(configFalse);
        assertFalse(serverConfigFalse.getDatabase().isAutoSave());
    }

    @Test
    @DisplayName("Should handle minimal valid configuration")
    void testMinimalConfiguration() {
        String yaml = """
            database:
              dimension: 64
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(64, serverConfig.getDatabase().getDimension());
        assertEquals("0.0.0.0", serverConfig.getServer().getHost());
        assertEquals(8080, serverConfig.getServer().getPort());
        assertEquals(16, serverConfig.getServer().getThreads());
        assertEquals(DistanceType.L2, serverConfig.getDatabase().getDistanceType());
        assertEquals(IndexType.HNSW, serverConfig.getDatabase().getIndexType());
        assertTrue(serverConfig.getDatabase().isAutoSave());
    }

    @Test
    @DisplayName("Should handle case-sensitive distance type")
    void testCaseSensitiveDistanceType() {
        String yaml = """
            database:
              dimension: 128
              distanceType: "L2"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(DistanceType.L2, serverConfig.getDatabase().getDistanceType());
    }

    @Test
    @DisplayName("Should handle case-sensitive index type")
    void testCaseSensitiveIndexType() {
        String yaml = """
            database:
              dimension: 128
              indexType: "HNSW"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(IndexType.HNSW, serverConfig.getDatabase().getIndexType());
    }

    // ========== Invalid Config Tests ==========

    @Test
    @DisplayName("Should throw exception for invalid distance type")
    void testInvalidDistanceType() {
        String yaml = """
            database:
              dimension: 128
              distanceType: "INVALID"
            """;

        Map<String, Object> config = new Yaml().load(yaml);

        assertThrows(IllegalArgumentException.class, () -> {
            new ServerConfig(config);
        });
    }

    @Test
    @DisplayName("Should throw exception for invalid index type")
    void testInvalidIndexType() {
        String yaml = """
            database:
              dimension: 128
              indexType: "INVALID"
            """;

        Map<String, Object> config = new Yaml().load(yaml);

        assertThrows(IllegalArgumentException.class, () -> {
            new ServerConfig(config);
        });
    }

    // ========== Zero and Negative Values Tests ==========

    @Test
    @DisplayName("Should handle zero values for numeric parameters")
    void testZeroValues() {
        String yaml = """
            server:
              port: 0
              threads: 0
            database:
              dimension: 128
              autoSaveInterval: 0
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(0, serverConfig.getServer().getPort());
        assertEquals(0, serverConfig.getServer().getThreads());
        assertEquals(0, serverConfig.getDatabase().getAutoSaveInterval());
    }

    @Test
    @DisplayName("Should handle negative values for numeric parameters")
    void testNegativeValues() {
        String yaml = """
            database:
              dimension: 128
              autoSaveInterval: -1
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals(-1, serverConfig.getDatabase().getAutoSaveInterval());
    }

    // ========== Special Characters in Paths ==========

    @Test
    @DisplayName("Should handle paths with spaces")
    void testPathsWithSpaces() {
        String yaml = """
            database:
              dimension: 128
              indexPath: "/path with spaces/data"
            logging:
              file: "/log path with spaces/vectordb.log"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals("/path with spaces/data", serverConfig.getDatabase().getIndexPath().toString());
        assertEquals("/log path with spaces/vectordb.log", serverConfig.getLogging().getFile());
    }

    @Test
    @DisplayName("Should handle paths with special characters")
    void testPathsWithSpecialCharacters() {
        String yaml = """
            database:
              dimension: 128
              indexPath: "/path-with_special.chars/data"
            """;

        Map<String, Object> config = new Yaml().load(yaml);
        ServerConfig serverConfig = new ServerConfig(config);

        assertEquals("/path-with_special.chars/data", serverConfig.getDatabase().getIndexPath().toString());
    }
}
