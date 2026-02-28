package com.csyangchsh.demo.vd.storage;

import com.csyangchsh.demo.vd.model.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Write-Ahead Log (WAL) for durable vector database operations.
 *
 * The WAL ensures durability by:
 * 1. Writing all mutations (insert/delete) to a log file before applying them
 * 2. Periodically checkpointing the main database file
 * 3. Allowing recovery from crash by replaying the WAL
 *
 * Benefits:
 * - No data loss on crash (up to last flush)
 * - Fast writes (async flush to disk)
 * - Automatic recovery on startup
 *
 * Log format (with UUID v7, deleted flag, text, and metadata):
 * - INSERT: [1 byte: type=1] [4 bytes: id_len] [id_len bytes: UUID string]
 *           [1 byte: deleted] [1 byte: has_text] [if true: 4 bytes: text_len + text bytes]
 *           [1 byte: has_metadata] [if true: metadata data]
 *           [4 bytes: dimension] [dimension*4: vector data]
 * - DELETE: [1 byte: type=2] [4 bytes: id_len] [id_len bytes: UUID string]
 * - CHECKPOINT: [1 byte: type=3] [8 bytes: checkpoint_id]
 */
public class WAL implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(WAL.class);

    private static final byte OP_INSERT = 1;
    private static final byte OP_DELETE = 2;
    private static final byte OP_CHECKPOINT = 3;

    private final Path walPath;
    private final long checkpointIntervalMs;
    private final ReentrantLock writeLock = new ReentrantLock();

    private FileOutputStream fileOutputStream;
    private DataOutputStream walOutputStream;
    private final ExecutorService flushExecutor;
    private volatile long lastCheckpointTime;
    private volatile long checkpointId;
    private volatile long walSize;

    /**
     * Create WAL with default checkpoint interval (5 minutes)
     */
    public WAL(String basePath) throws IOException {
        this(basePath, 5 * 60 * 1000);
    }

    /**
     * Create WAL with custom checkpoint interval
     *
     * @param basePath           Base path for WAL and checkpoint files
     * @param checkpointIntervalMs Milliseconds between checkpoints
     */
    public WAL(String basePath, long checkpointIntervalMs) throws IOException {
        this.walPath = Paths.get(basePath + ".wal");
        this.checkpointIntervalMs = checkpointIntervalMs;
        this.lastCheckpointTime = System.currentTimeMillis();
        this.checkpointId = 0;
        this.walSize = 0;

        // Open WAL for appending
        openWAL();

        // Single-threaded executor for async flushes
        this.flushExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "WAL-Flush");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Log an insert operation with UUID v7, deleted flag, text, and metadata
     */
    public void logInsert(String id, float[] vector, boolean deleted, String text, Metadata metadata) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeByte(OP_INSERT);

        // Write UUID as UTF-8 string
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        dos.writeInt(idBytes.length);
        dos.write(idBytes);

        // Write deleted flag
        dos.writeBoolean(deleted);

        // Write text (optional)
        if (text == null) {
            dos.writeBoolean(false);
        } else {
            dos.writeBoolean(true);
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(textBytes.length);
            dos.write(textBytes);
        }

        // Write metadata (optional)
        if (metadata == null) {
            dos.writeBoolean(false);
        } else {
            dos.writeBoolean(true);
            metadata.save(dos);
        }

        // Write vector data
        dos.writeInt(vector.length);
        for (float v : vector) {
            dos.writeFloat(v);
        }

        writeToWAL(baos.toByteArray());
    }

    /**
     * Convenience method: log insert with deleted=false (for new vectors)
     */
    public void logInsert(String id, float[] vector, String text, Metadata metadata) throws IOException {
        logInsert(id, vector, false, text, metadata);
    }

    /**
     * Convenience method: log insert with deleted=false, no text/metadata (for tests)
     */
    public void logInsert(String id, float[] vector) throws IOException {
        logInsert(id, vector, false, null, null);
    }

    /**
     * Log a delete operation with UUID v7
     */
    public void logDelete(String id) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeByte(OP_DELETE);

        // Write UUID as UTF-8 string
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        dos.writeInt(idBytes.length);
        dos.write(idBytes);

        writeToWAL(baos.toByteArray());
    }

    /**
     * Log a checkpoint marker
     */
    public void logCheckpoint(long checkpointId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeByte(OP_CHECKPOINT);
        dos.writeLong(checkpointId);

        writeToWAL(baos.toByteArray());
        this.checkpointId = checkpointId;
        this.lastCheckpointTime = System.currentTimeMillis();
    }

    /**
     * Check if checkpoint is needed
     */
    public boolean shouldCheckpoint() {
        return (System.currentTimeMillis() - lastCheckpointTime) >= checkpointIntervalMs;
    }

    /**
     * Get the last checkpoint ID
     */
    public long getLastCheckpointId() {
        return checkpointId;
    }

    /**
     * Get the current WAL size in bytes
     */
    public long getWALSize() {
        return walSize;
    }

    /**
     * Get the WAL file path
     */
    public Path getWalPath() {
        return walPath;
    }

    /**
     * Async flush to disk
     */
    public void asyncFlush() {
        flushExecutor.submit(() -> {
            try {
                sync();
            } catch (IOException e) {
                logger.error("Failed to sync WAL", e);
            }
        });
    }

    /**
     * Force sync to disk
     */
    public void sync() throws IOException {
        writeLock.lock();
        try {
            if (walOutputStream != null) {
                walOutputStream.flush();
            }
            if (fileOutputStream != null) {
                // Force sync to disk
                fileOutputStream.getFD().sync();
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Truncate WAL after successful checkpoint
     */
    public void truncate() throws IOException {
        writeLock.lock();
        try {
            closeWAL();
            Files.deleteIfExists(walPath);
            openWAL();
            walSize = 0;
            logger.debug("WAL truncated after checkpoint {}", checkpointId);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        flushExecutor.shutdown();
        try {
            if (!flushExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                flushExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        closeWAL();
    }

    /**
     * Replay WAL entries
     * Returns a WALRecovery with operations to apply
     */
    public static WALRecovery recover(String basePath) throws IOException {
        Path walPath = Paths.get(basePath + ".wal");
        if (!Files.exists(walPath)) {
            return new WALRecovery(0, java.util.Collections.emptyList());
        }

        List<WALEntry> entries = new ArrayList<>();
        long lastCheckpointId = 0;

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(walPath.toFile())))) {

            while (in.available() > 0) {
                byte opType = in.readByte();

                switch (opType) {
                    case OP_INSERT: {
                        // Read UUID string
                        int idLen = in.readInt();
                        byte[] idBytes = new byte[idLen];
                        in.readFully(idBytes);
                        String id = new String(idBytes, StandardCharsets.UTF_8);

                        // Read deleted flag
                        boolean deleted = in.readBoolean();

                        // Read text (optional)
                        String text = null;
                        boolean hasText = in.readBoolean();
                        if (hasText) {
                            int textLen = in.readInt();
                            byte[] textBytes = new byte[textLen];
                            in.readFully(textBytes);
                            text = new String(textBytes, StandardCharsets.UTF_8);
                        }

                        // Read metadata (optional)
                        Metadata metadata = null;
                        boolean hasMetadata = in.readBoolean();
                        if (hasMetadata) {
                            metadata = Metadata.load(in);
                        }

                        // Read vector data
                        int dim = in.readInt();
                        float[] vector = new float[dim];
                        for (int i = 0; i < dim; i++) {
                            vector[i] = in.readFloat();
                        }

                        entries.add(new WALEntry(OperationType.INSERT, id, vector, deleted, text, metadata));
                        break;
                    }
                    case OP_DELETE: {
                        // Read UUID string
                        int idLen = in.readInt();
                        byte[] idBytes = new byte[idLen];
                        in.readFully(idBytes);
                        String id = new String(idBytes, StandardCharsets.UTF_8);

                        entries.add(new WALEntry(OperationType.DELETE, id, null, null, null));
                        break;
                    }
                    case OP_CHECKPOINT: {
                        lastCheckpointId = in.readLong();
                        // Clear entries before this checkpoint
                        entries.clear();
                        break;
                    }
                    default:
                        logger.warn("Unknown WAL operation type: {}", opType);
                        break;
                }
            }
        }

        return new WALRecovery(lastCheckpointId, entries);
    }

    // ========== Inner classes ==========

    public enum OperationType {
        INSERT, DELETE
    }

    public static record WALEntry(OperationType type, String id, float[] vector, boolean deleted, String text, Metadata metadata) {
        /**
         * Convenience constructor for INSERT with deleted=false
         */
        public WALEntry(OperationType type, String id, float[] vector, String text, Metadata metadata) {
            this(type, id, vector, false, text, metadata);
        }

        /**
         * Convenience constructor for INSERT with deleted=false, no text/metadata
         */
        public WALEntry(OperationType type, String id, float[] vector) {
            this(type, id, vector, false, null, null);
        }
    }

    public static record WALRecovery(long lastCheckpointId, List<WALEntry> entries) {
    }

    // ========== Private methods ==========

    private void openWAL() throws IOException {
        // Create parent directory if needed
        if (walPath.getParent() != null) {
            Files.createDirectories(walPath.getParent());
        }

        // Open for appending
        fileOutputStream = new FileOutputStream(walPath.toFile(), true);
        walOutputStream = new DataOutputStream(new BufferedOutputStream(fileOutputStream));
    }

    private void closeWAL() throws IOException {
        if (walOutputStream != null) {
            walOutputStream.close();
            walOutputStream = null;
        }
        if (fileOutputStream != null) {
            fileOutputStream.close();
            fileOutputStream = null;
        }
    }

    private void writeToWAL(byte[] data) throws IOException {
        writeLock.lock();
        try {
            walOutputStream.write(data);
            walSize += data.length;
        } finally {
            writeLock.unlock();
        }
    }
}
