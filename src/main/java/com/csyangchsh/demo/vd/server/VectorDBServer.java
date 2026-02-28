package com.csyangchsh.demo.vd.server;

import com.csyangchsh.demo.vd.api.VectorDB;
import com.csyangchsh.demo.vd.model.DistanceType;
import com.csyangchsh.demo.vd.model.IndexType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VectorDB Server - Standalone vector database service
 *
 * Features:
 * - HTTP REST API
 * - YAML configuration
 * - Hot reload configuration
 * - Metrics and monitoring
 * - Graceful shutdown
 * - Auto-save with WAL
 *
 * Usage:
 * ```bash
 * # Start with default config
 * java -cp vector-db.jar com.csyangchsh.demo.vd.server.VectorDBServer
 *
 * # Start with custom config
 * java -cp vector-db.jar com.csyangchsh.demo.vd.server.VectorDBServer --config /path/to/config.yaml
 *
 * # Start in background
 * java -cp vector-db.jar com.csyangchsh.demo.vd.server.VectorDBServer --daemon
 * ```
 */
public class VectorDBServer {

    private static final Logger logger = LoggerFactory.getLogger(VectorDBServer.class);

    private final ServerConfig config;
    private final MetricsCollector metrics;
    private final AtomicBoolean running;
    private NettyHttpServer httpServer;
    private ScheduledExecutorService scheduler;
    private VectorDB database;  // Store database reference for save on shutdown

    public VectorDBServer(ServerConfig config) {
        this.config = config;
        this.metrics = new MetricsCollector();
        this.running = new AtomicBoolean(false);
    }

    /**
     * Start the server
     */
    public void start() throws InterruptedException {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting VectorDB server...");
            logger.info("Configuration: host={}, port={}, dimension={}, indexType={}",
                    config.getServer().getHost(),
                    config.getServer().getPort(),
                    config.getDatabase().getDimension(),
                    config.getDatabase().getIndexType());

            try {
                // Initialize database (load existing if available)
                this.database = initializeDatabase();

                // Initialize Netty HTTP server
                this.httpServer = new NettyHttpServer(database, metrics, config);

                // Start auto-save if configured
                if (config.getDatabase().isAutoSave()) {
                    startAutoSave(database);
                }

                // Register shutdown hook
                Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

                logger.info("VectorDB server started successfully");

                // Start HTTP server (blocking call)
                httpServer.start();
            } catch (Exception e) {
                logger.error("Failed to start server", e);
                running.set(false);
                throw new RuntimeException("Failed to start server", e);
            }
        }
    }

    /**
     * Stop the server
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping VectorDB server...");
            shutdown();
        }
    }

    /**
     * Check if server is running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Shutdown the server gracefully
     */
    private void shutdown() {
        logger.info("Shutting down VectorDB server...");

        // Save database before stopping
        if (database != null) {
            try {
                Path dbFile = config.getDatabase().getIndexPath().resolve("database.bin");
                logger.info("Saving database to {}...", dbFile);
                database.save(dbFile.toString());
                logger.info("Database saved: {} vectors", database.size());
            } catch (Exception e) {
                logger.error("Failed to save database on shutdown", e);
            }
        }

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (httpServer != null) {
            httpServer.shutdown();
        }

        metrics.setServing(false);
        logger.info("VectorDB server stopped");
    }

    /**
     * Initialize database based on configuration
     */
    private VectorDB initializeDatabase() throws IOException {
        // Create index directory if needed
        Path indexPath = config.getDatabase().getIndexPath();
        Files.createDirectories(indexPath.getParent());

        // Try to load existing database
        Path dbFile = indexPath.resolve("database.bin");
        VectorDB database;

        if (Files.exists(dbFile)) {
            logger.info("Loading existing database from {}", dbFile);
            try {
                database = VectorDB.load(dbFile.toString());
                logger.info("Loaded database: {} vectors", database.size());

                // Check if index type matches configuration
                IndexType configuredIndexType = config.getDatabase().getIndexType();
                IndexType loadedIndexType = database.getDefaultCollection().getIndexType();

                if (loadedIndexType != configuredIndexType) {
                    logger.warn("Index type mismatch: configured={}, loaded={}. Rebuilding index...",
                            configuredIndexType, loadedIndexType);
                    database = rebuildIndex(database, configuredIndexType);
                }
            } catch (Exception e) {
                logger.warn("Failed to load existing database, creating new one: {}", e.getMessage());
                database = createNewDatabase();
            }
        } else {
            logger.info("Creating new database");
            database = createNewDatabase();
        }

        return database;
    }

    /**
     * Rebuild database with specified index type
     */
    private VectorDB rebuildIndex(VectorDB oldDatabase, IndexType newIndexType) {
        int dimension = config.getDatabase().getDimension();
        DistanceType distanceType = config.getDatabase().getDistanceType();

        // Create new database with configured index type
        VectorDB newDatabase = switch (newIndexType) {
            case FLAT -> VectorDB.create(dimension, distanceType, IndexType.FLAT);
            case COMPACT_FLAT -> VectorDB.create(dimension, distanceType, IndexType.COMPACT_FLAT);
            case HNSW -> {
                int M = config.getHnsw() != null ? config.getHnsw().getM() : 16;
                yield VectorDB.createWithHNSW(dimension, distanceType, M);
            }
            case PQ -> VectorDB.create(dimension, distanceType, IndexType.PQ);
        };

        // Migrate all vectors from old database to new database
        int migratedCount = 0;
        var oldCollection = oldDatabase.getDefaultCollection();
        var newCollection = newDatabase.getDefaultCollection();

        // Get all vectors from old collection and insert into new collection
        for (var vector : oldCollection.getAllVectors()) {
            if (!vector.isDeleted()) {
                newCollection.insert(vector);
                migratedCount++;
            }
        }

        logger.info("Index rebuild completed: {} vectors migrated to {}", migratedCount, newIndexType);
        return newDatabase;
    }

    /**
     * Create a new database with configured settings
     */
    private VectorDB createNewDatabase() {
        int dimension = config.getDatabase().getDimension();
        DistanceType distanceType = config.getDatabase().getDistanceType();
        IndexType indexType = config.getDatabase().getIndexType();

        return switch (indexType) {
            case FLAT -> VectorDB.create(dimension, distanceType);
            case COMPACT_FLAT -> {
                // Use CompactFlatIndex via create with IndexType
                yield VectorDB.create(dimension, distanceType);
            }
            case HNSW -> {
                int M = config.getHnsw() != null ? config.getHnsw().getM() : 16;
                yield VectorDB.createWithHNSW(dimension, distanceType, M);
            }
            case PQ -> {
                // PQ requires training, create with default first
                yield VectorDB.create(dimension, distanceType);
            }
        };
    }

    /**
     * Start auto-save scheduler
     */
    private void startAutoSave(VectorDB database) {
        int intervalSeconds = config.getDatabase().getAutoSaveInterval();
        Path dbFile = config.getDatabase().getIndexPath().resolve("database.bin");

        scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                logger.debug("Auto-saving database to {}", dbFile);
                database.save(dbFile.toString());
                logger.debug("Auto-save completed: {} vectors", database.size());
            } catch (Exception e) {
                logger.error("Auto-save failed", e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

        logger.info("Auto-save enabled: interval={}s, path={}", intervalSeconds, dbFile);
    }

    // ========== Main Entry Point ==========

    public static void main(String[] args) {
        try {
            // Parse command line arguments
            ServerConfig config = parseArguments(args);

            // Create and start server
            VectorDBServer server = new VectorDBServer(config);

            if (!isDaemonMode(args)) {
                // Add console prompt for non-daemon mode
                startConsolePrompt(server);
            }

            server.start();

            // Keep running until interrupted
            while (server.isRunning()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } catch (Exception e) {
            logger.error("Failed to start server", e);
            System.exit(1);
        }
    }

    /**
     * Parse command line arguments and load configuration
     */
    private static ServerConfig parseArguments(String[] args) throws IOException {
        String configPath = getConfigPath(args);

        logger.info("Loading configuration from {}", configPath);

        // Load YAML configuration
        Map<String, Object> configMap = loadYamlConfig(configPath);

        return new ServerConfig(configMap);
    }

    /**
     * Get configuration path from arguments
     */
    private static String getConfigPath(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return "config.yaml"; // Default config file
    }

    /**
     * Check if running in daemon mode
     */
    private static boolean isDaemonMode(String[] args) {
        for (String arg : args) {
            if ("--daemon".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Load YAML configuration from file
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYamlConfig(String path) throws IOException {
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();

        try (FileInputStream fis = new FileInputStream(path);
             InputStreamReader reader = new InputStreamReader(fis)) {
            return yaml.load(reader);
        }
    }

    /**
     * Start interactive console prompt
     */
    private static void startConsolePrompt(VectorDBServer server) {
        Thread consoleThread = new Thread(() -> {
            java.io.BufferedReader console = new java.io.BufferedReader(
                    new java.io.InputStreamReader(System.in));

            System.out.println("\n=== VectorDB Server Console ===");
            System.out.println("Commands: status, metrics, stop, help, quit");

            try {
                while (server.isRunning()) {
                    System.out.print("\n> ");
                    String command = console.readLine();

                    if (command == null) {
                        break;
                    }

                    switch (command.trim().toLowerCase()) {
                        case "status" -> System.out.println("Server is " + (server.isRunning() ? "RUNNING" : "STOPPED"));
                        case "metrics" -> System.out.println(server.metrics.getMetricsJson());
                        case "stop" -> {
                            System.out.println("Stopping server...");
                            server.stop();
                        }
                        case "help" -> printHelp();
                        case "quit", "exit" -> {
                            System.out.println("Exiting...");
                            server.stop();
                        }
                        case "" -> {}
                        default -> System.out.println("Unknown command: " + command);
                    }
                }
            } catch (IOException e) {
                logger.error("Console error", e);
            }
        }, "console-prompt");

        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    private static void printHelp() {
        System.out.println("""
            Available commands:
              status   - Show server status
              metrics  - Show detailed metrics
              stop     - Stop the server
              help     - Show this help
              quit     - Exit console (server continues running)
            """);
    }
}
