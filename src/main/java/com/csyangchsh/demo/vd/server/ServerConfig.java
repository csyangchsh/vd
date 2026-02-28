package com.csyangchsh.demo.vd.server;

import com.csyangchsh.demo.vd.model.DistanceType;
import com.csyangchsh.demo.vd.model.IndexType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Server configuration loaded from YAML file
 *
 * Example config.yaml:
 * ```yaml
 * server:
 *   host: "0.0.0.0"
 *   port: 8080
 *   threads: 16
 *
 * database:
 *   dimension: 128
 *   distanceType: "L2"
 *   indexType: "HNSW"
 *   indexPath: "/data/vectordb/index"
 *   autoSave: true
 *   autoSaveInterval: 300  # seconds
 *
 * hnsw:
 *   M: 16
 *   efConstruction: 200
 *
 * logging:
 *   level: "INFO"
 *   file: "/var/log/vectordb/server.log"
 * ```
 */
public class ServerConfig {

    // Server settings
    private final ServerSettings server;
    // Database settings
    private final DatabaseSettings database;
    // HNSW settings (optional)
    private final HNSWSettings hnsw;
    // Logging settings
    private final LoggingSettings logging;

    @SuppressWarnings("unchecked")
    public ServerConfig(Map<String, Object> config) {
        Map<String, Object> serverMap = (Map<String, Object>) config.get("server");
        Map<String, Object> databaseMap = (Map<String, Object>) config.get("database");
        Map<String, Object> hnswMap = (Map<String, Object>) config.get("hnsw");
        Map<String, Object> loggingMap = (Map<String, Object>) config.get("logging");

        this.server = new ServerSettings(serverMap);
        this.database = new DatabaseSettings(databaseMap);
        this.hnsw = hnswMap != null ? new HNSWSettings(hnswMap) : null;
        this.logging = loggingMap != null ? new LoggingSettings(loggingMap) : new LoggingSettings();
    }

    public ServerSettings getServer() {
        return server;
    }

    public DatabaseSettings getDatabase() {
        return database;
    }

    public HNSWSettings getHnsw() {
        return hnsw;
    }

    public LoggingSettings getLogging() {
        return logging;
    }

    /**
     * Server connection settings
     */
    public static class ServerSettings {
        private final String host;
        private final int port;
        private final int threads;
        private final int maxRequestSize;

        public ServerSettings(Map<String, Object> map) {
            if (map == null) {
                map = Map.of();
            }
            this.host = map.getOrDefault("host", "0.0.0.0").toString();
            Object portObj = map.getOrDefault("port", 8080);
            this.port = toInt(portObj, 8080);
            Object threadsObj = map.getOrDefault("threads", 16);
            this.threads = toInt(threadsObj, 16);
            Object maxRequestSizeObj = map.getOrDefault("maxRequestSize", 100);
            this.maxRequestSize = toInt(maxRequestSizeObj, 100);
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public int getThreads() {
            return threads;
        }

        public int getMaxRequestSize() {
            return maxRequestSize;
        }

        private static int toInt(Object obj, int defaultValue) {
            if (obj instanceof Number) {
                return ((Number) obj).intValue();
            }
            if (obj instanceof String) {
                try {
                    return Integer.parseInt((String) obj);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
            return defaultValue;
        }
    }

    /**
     * Database configuration
     */
    public static class DatabaseSettings {
        private final int dimension;
        private final DistanceType distanceType;
        private final IndexType indexType;
        private final Path indexPath;
        private final boolean autoSave;
        private final int autoSaveInterval;

        @SuppressWarnings("unchecked")
        public DatabaseSettings(Map<String, Object> map) {
            if (map == null) {
                throw new IllegalArgumentException("Database settings map cannot be null");
            }

            Object dimObj = map.get("dimension");
            if (dimObj == null) {
                throw new IllegalArgumentException("Database dimension is required");
            }
            this.dimension = toInt(dimObj, 128);

            this.distanceType = DistanceType.valueOf(map.getOrDefault("distanceType", "L2").toString().toUpperCase());
            this.indexType = IndexType.valueOf(map.getOrDefault("indexType", "HNSW").toString().toUpperCase());

            String pathStr = map.getOrDefault("indexPath", "./data/index").toString();
            this.indexPath = Paths.get(pathStr);

            // Default to true for data safety (unless explicitly set to false)
            Object autoSaveObj = map.get("autoSave");
            this.autoSave = autoSaveObj == null || Boolean.TRUE.equals(autoSaveObj);
            // Default auto-save interval: 60 seconds (1 minute)
            // Provides good balance between data safety and I/O efficiency
            Object intervalObj = map.getOrDefault("autoSaveInterval", 60);
            this.autoSaveInterval = toInt(intervalObj, 60);
        }

        public int getDimension() {
            return dimension;
        }

        public DistanceType getDistanceType() {
            return distanceType;
        }

        public IndexType getIndexType() {
            return indexType;
        }

        public Path getIndexPath() {
            return indexPath;
        }

        public boolean isAutoSave() {
            return autoSave;
        }

        public int getAutoSaveInterval() {
            return autoSaveInterval;
        }

        private static int toInt(Object obj, int defaultValue) {
            if (obj instanceof Number) {
                return ((Number) obj).intValue();
            }
            if (obj instanceof String) {
                try {
                    return Integer.parseInt((String) obj);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
            return defaultValue;
        }
    }

    /**
     * HNSW-specific configuration
     */
    public static class HNSWSettings {
        private final int M;
        private final int efConstruction;

        public HNSWSettings(Map<String, Object> map) {
            if (map == null) {
                map = Map.of();
            }
            Object mObj = map.getOrDefault("M", 16);
            this.M = toInt(mObj, 16);
            Object efObj = map.getOrDefault("efConstruction", 200);
            this.efConstruction = toInt(efObj, 200);
        }

        public int getM() {
            return M;
        }

        public int getEfConstruction() {
            return efConstruction;
        }

        private static int toInt(Object obj, int defaultValue) {
            if (obj instanceof Number) {
                return ((Number) obj).intValue();
            }
            if (obj instanceof String) {
                try {
                    return Integer.parseInt((String) obj);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
            return defaultValue;
        }
    }

    /**
     * Logging configuration
     */
    public static class LoggingSettings {
        private final String level;
        private final String file;

        public LoggingSettings() {
            this.level = "INFO";
            this.file = null;
        }

        public LoggingSettings(Map<String, Object> map) {
            if (map == null) {
                map = Map.of();
            }
            this.level = map.getOrDefault("level", "INFO").toString();
            this.file = (String) map.get("file");
        }

        public String getLevel() {
            return level;
        }

        public String getFile() {
            return file;
        }
    }
}
