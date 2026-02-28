package com.csyangchsh.demo.vd.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * UUID v7 (RFC 9562) utility for generating time-ordered, unique identifiers.
 *
 * UUID v7 combines:
 * - 48-bit Unix timestamp (milliseconds since epoch) - provides time ordering
 * - 74-bit random data - provides uniqueness and security
 *
 * Format: xxxxxxxx-xxxx-7xxx-xxxx-xxxxxxxxxxxx
 * - Version 7 (0111) in bits 48-51
 * - Variant (10xx) in bits 64-65
 *
 * Advantages:
 * - Time-ordered (sortable)
 * - Client-side generation (no coordination needed)
 * - Globally unique (distributed-friendly)
 * - More efficient than UUID v1
 */
public final class UUIDv7 {

    private UUIDv7() {
        // Utility class
    }

    /**
     * Generate a new UUID v7
     *
     * @return UUID v7 as String (standard format: xxxxxxxx-xxxx-7xxx-xxxx-xxxxxxxxxxxx)
     */
    public static String generate() {
        return generateUUID().toString();
    }

    /**
     * Generate a new UUID v7 without dashes (compact format)
     *
     * @return UUID v7 without dashes (32 hex characters)
     */
    public static String generateCompact() {
        return generateUUID().toString().replace("-", "");
    }

    /**
     * Generate a new UUID v7 as byte array
     *
     * @return 16 bytes representing the UUID
     */
    public static byte[] generateBytes() {
        return uuidToBytes(generateUUID());
    }

    /**
     * Extract timestamp from UUID v7
     *
     * @param uuidStr UUID v7 string
     * @return Unix timestamp in milliseconds
     */
    public static long getTimestamp(String uuidStr) {
        UUID uuid = UUID.fromString(uuidStr);
        return getTimestamp(uuid);
    }

    /**
     * Extract timestamp from UUID v7
     *
     * @param uuid UUID object
     * @return Unix timestamp in milliseconds
     */
    public static long getTimestamp(UUID uuid) {
        long msb = uuid.getMostSignificantBits();

        // Extract timestamp (bits 0-47)
        // time_low (32 bits) + time_mid (16 bits) + time_high (12 bits of version field)
        long timestamp = ((msb & 0xFFFFFFFFFFFFL) << 4) | ((msb >>> 48) & 0x0FFFL);
        return timestamp;
    }

    /**
     * Check if a UUID is version 7
     *
     * @param uuidStr UUID string to check
     * @return true if the UUID is version 7
     */
    public static boolean isUUIDv7(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            return isUUIDv7(uuid);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Check if a UUID is version 7
     *
     * @param uuid UUID object to check
     * @return true if the UUID is version 7
     */
    public static boolean isUUIDv7(UUID uuid) {
        // Version is in bits 48-51 of MSB
        // Version 7 = 0111
        long version = (uuid.getMostSignificantBits() >>> 48) & 0x0FL;
        return version == 7;
    }

    // ========== Private methods ==========

    /**
     * Generate a new UUID v7
     */
    private static UUID generateUUID() {
        // Get current time in milliseconds (Unix epoch)
        long timestamp = System.currentTimeMillis();

        // Generate random data for the rest (clock_seq + node)
        // 64 bits of randomness
        byte[] random = new byte[8];
        java.util.concurrent.ThreadLocalRandom.current().nextBytes(random);

        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.order(ByteOrder.BIG_ENDIAN);

        // Construct MSB (most significant bits) - 64 bits
        // Format: [time_high(12)+version(4)][time_mid(16)][time_low(32)]
        long msb;
        long timeLow = timestamp & 0xFFFFFFFFL;          // lower 32 bits
        long timeMid = (timestamp >> 32) & 0xFFFFL;      // bits 32-47
        long timeHigh = (timestamp >> 48) & 0xFFFL;      // bits 48-59 (6 bits)

        // Version 7 = 0111 in bits 48-51 of time_high
        // timeHigh currently has 6 bits, we need to shift left and set version
        // But first, let's reorganize:
        // We have 48 bits of timestamp, need to place them correctly
        // time_low = 32 bits: bits 0-31 of timestamp
        // time_mid = 16 bits: bits 32-47 of timestamp
        // time_high = 12 bits: bits 48-59 of timestamp (but we shift to make room for version)

        // Reconstruct: time_high (12 bits) + version (4 bits)
        long timeHighAndVersion = (timeHigh << 4) | 0x7000L;  // version 7
        long timeMidAndHigh = (timeMid << 48) | (timeHighAndVersion << 32);
        msb = timeLow | timeMidAndHigh;

        // Construct LSB (least significant bits) - 64 bits
        // variant (2 bits: 10) + clock_seq (6 bits) + node (48 bits)
        byte[] randomBits = random;
        long lsb = ByteBuffer.wrap(randomBits).getLong();
        // Set variant bits (10) in bits 64-65
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

        bb.putLong(msb);
        bb.putLong(lsb);

        bb.rewind();
        long msbOut = bb.getLong();
        long lsbOut = bb.getLong();

        return new UUID(msbOut, lsbOut);
    }

    /**
     * Convert UUID to byte array
     */
    private static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }
}
