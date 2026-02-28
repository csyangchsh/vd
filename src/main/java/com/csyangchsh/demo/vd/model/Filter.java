package com.csyangchsh.demo.vd.model;

/**
 * Filter condition for metadata-based filtering during vector search.
 *
 * Supports various comparison operations on metadata fields.
 *
 * Example usage:
 * <pre>
 * // Simple equals filter
 * Filter filter = Filter.eq("category", "news");
 *
 * // Range filter
 * Filter filter = Filter.gt("score", 0.5);
 *
 * // Logical AND
 * Filter filter = Filter.and(
 *     Filter.eq("category", "news"),
 *     Filter.gte("timestamp", 1234567890L)
 * );
 *
 * // Logical OR
 * Filter filter = Filter.or(
 *     Filter.eq("status", "active"),
 *     Filter.eq("status", "pending")
 * );
 *
 * // NOT filter
 * Filter filter = Filter.not(Filter.eq("deleted", true));
 * </pre>
 */
public abstract class Filter {

    /**
     * Evaluate filter against metadata
     *
     * @param metadata The metadata to evaluate against
     * @return true if the metadata matches the filter
     */
    public abstract boolean matches(Metadata metadata);

    // ========== Comparison Filters ==========

    /**
     * Equals filter
     */
    public static Filter eq(String key, String value) {
        return new EqFilter(key, new Metadata.Value(value));
    }

    public static Filter eq(String key, Long value) {
        return new EqFilter(key, new Metadata.Value(value));
    }

    public static Filter eq(String key, Double value) {
        return new EqFilter(key, new Metadata.Value(value));
    }

    public static Filter eq(String key, Boolean value) {
        return new EqFilter(key, new Metadata.Value(value));
    }

    /**
     * Not equals filter
     */
    public static Filter ne(String key, String value) {
        return new NeFilter(key, new Metadata.Value(value));
    }

    public static Filter ne(String key, Long value) {
        return new NeFilter(key, new Metadata.Value(value));
    }

    public static Filter ne(String key, Double value) {
        return new NeFilter(key, new Metadata.Value(value));
    }

    public static Filter ne(String key, Boolean value) {
        return new NeFilter(key, new Metadata.Value(value));
    }

    /**
     * Greater than filter (for numeric values)
     */
    public static Filter gt(String key, Number value) {
        return new GtFilter(key, value.doubleValue());
    }

    /**
     * Greater than or equal filter (for numeric values)
     */
    public static Filter gte(String key, Number value) {
        return new GteFilter(key, value.doubleValue());
    }

    /**
     * Less than filter (for numeric values)
     */
    public static Filter lt(String key, Number value) {
        return new LtFilter(key, value.doubleValue());
    }

    /**
     * Less than or equal filter (for numeric values)
     */
    public static Filter lte(String key, Number value) {
        return new LteFilter(key, value.doubleValue());
    }

    /**
     * Contains filter (for string values, checks if string contains substring)
     */
    public static Filter contains(String key, String substring) {
        return new ContainsFilter(key, substring);
    }

    /**
     * Starts with filter (for string values)
     */
    public static Filter startsWith(String key, String prefix) {
        return new StartsWithFilter(key, prefix);
    }

    /**
     * Ends with filter (for string values)
     */
    public static Filter endsWith(String key, String suffix) {
        return new EndsWithFilter(key, suffix);
    }

    /**
     * Exists filter (checks if key exists in metadata)
     */
    public static Filter exists(String key) {
        return new ExistsFilter(key);
    }

    // ========== Logical Filters ==========

    /**
     * Logical AND - all filters must match
     */
    public static Filter and(Filter... filters) {
        return new AndFilter(filters);
    }

    /**
     * Logical OR - at least one filter must match
     */
    public static Filter or(Filter... filters) {
        return new OrFilter(filters);
    }

    /**
     * Logical NOT - filter must not match
     */
    public static Filter not(Filter filter) {
        return new NotFilter(filter);
    }

    // ========== Filter Implementations ==========

    private static class EqFilter extends Filter {
        private final String key;
        private final Metadata.Value value;

        EqFilter(String key, Metadata.Value value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public boolean matches(Metadata metadata) {
            Metadata.Value v = metadata.get(key);
            return value.equals(v);
        }

        @Override
        public String toString() {
            return key + " == " + value;
        }
    }

    private static class NeFilter extends Filter {
        private final String key;
        private final Metadata.Value value;

        NeFilter(String key, Metadata.Value value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public boolean matches(Metadata metadata) {
            Metadata.Value v = metadata.get(key);
            return !value.equals(v);
        }

        @Override
        public String toString() {
            return key + " != " + value;
        }
    }

    private static class GtFilter extends Filter {
        private final String key;
        private final double value;

        GtFilter(String key, double value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public boolean matches(Metadata metadata) {
            Metadata.Value v = metadata.get(key);
            if (v == null) return false;
            return switch (v.getType()) {
                case LONG -> v.asLong() > value;
                case DOUBLE -> v.asDouble() > value;
                default -> false;
            };
        }

        @Override
        public String toString() {
            return key + " > " + value;
        }
    }

    private static class GteFilter extends Filter {
        private final String key;
        private final double value;

        GteFilter(String key, double value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public boolean matches(Metadata metadata) {
            Metadata.Value v = metadata.get(key);
            if (v == null) return false;
            return switch (v.getType()) {
                case LONG -> v.asLong() >= value;
                case DOUBLE -> v.asDouble() >= value;
                default -> false;
            };
        }

        @Override
        public String toString() {
            return key + " >= " + value;
        }
    }

    private static class LtFilter extends Filter {
        private final String key;
        private final double value;

        LtFilter(String key, double value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public boolean matches(Metadata metadata) {
            Metadata.Value v = metadata.get(key);
            if (v == null) return false;
            return switch (v.getType()) {
                case LONG -> v.asLong() < value;
                case DOUBLE -> v.asDouble() < value;
                default -> false;
            };
        }

        @Override
        public String toString() {
            return key + " < " + value;
        }
    }

    private static class LteFilter extends Filter {
        private final String key;
        private final double value;

        LteFilter(String key, double value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public boolean matches(Metadata metadata) {
            Metadata.Value v = metadata.get(key);
            if (v == null) return false;
            return switch (v.getType()) {
                case LONG -> v.asLong() <= value;
                case DOUBLE -> v.asDouble() <= value;
                default -> false;
            };
        }

        @Override
        public String toString() {
            return key + " <= " + value;
        }
    }

    private static class ContainsFilter extends Filter {
        private final String key;
        private final String substring;

        ContainsFilter(String key, String substring) {
            this.key = key;
            this.substring = substring;
        }

        @Override
        public boolean matches(Metadata metadata) {
            String v = metadata.getString(key);
            return v != null && v.contains(substring);
        }

        @Override
        public String toString() {
            return key + " contains '" + substring + "'";
        }
    }

    private static class StartsWithFilter extends Filter {
        private final String key;
        private final String prefix;

        StartsWithFilter(String key, String prefix) {
            this.key = key;
            this.prefix = prefix;
        }

        @Override
        public boolean matches(Metadata metadata) {
            String v = metadata.getString(key);
            return v != null && v.startsWith(prefix);
        }

        @Override
        public String toString() {
            return key + " startsWith '" + prefix + "'";
        }
    }

    private static class EndsWithFilter extends Filter {
        private final String key;
        private final String suffix;

        EndsWithFilter(String key, String suffix) {
            this.key = key;
            this.suffix = suffix;
        }

        @Override
        public boolean matches(Metadata metadata) {
            String v = metadata.getString(key);
            return v != null && v.endsWith(suffix);
        }

        @Override
        public String toString() {
            return key + " endsWith '" + suffix + "'";
        }
    }

    private static class ExistsFilter extends Filter {
        private final String key;

        ExistsFilter(String key) {
            this.key = key;
        }

        @Override
        public boolean matches(Metadata metadata) {
            return metadata.contains(key);
        }

        @Override
        public String toString() {
            return key + " exists";
        }
    }

    private static class AndFilter extends Filter {
        private final Filter[] filters;

        AndFilter(Filter[] filters) {
            this.filters = filters;
        }

        @Override
        public boolean matches(Metadata metadata) {
            for (Filter f : filters) {
                if (!f.matches(metadata)) return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "AND(" + String.join(", ", java.util.Arrays.stream(filters).map(Filter::toString).toArray(String[]::new)) + ")";
        }
    }

    private static class OrFilter extends Filter {
        private final Filter[] filters;

        OrFilter(Filter[] filters) {
            this.filters = filters;
        }

        @Override
        public boolean matches(Metadata metadata) {
            for (Filter f : filters) {
                if (f.matches(metadata)) return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return "OR(" + String.join(", ", java.util.Arrays.stream(filters).map(Filter::toString).toArray(String[]::new)) + ")";
        }
    }

    private static class NotFilter extends Filter {
        private final Filter filter;

        NotFilter(Filter filter) {
            this.filter = filter;
        }

        @Override
        public boolean matches(Metadata metadata) {
            return !filter.matches(metadata);
        }

        @Override
        public String toString() {
            return "NOT(" + filter + ")";
        }
    }

    /**
     * Always true filter (no filtering)
     */
    public static Filter all() {
        return new Filter() {
            @Override
            public boolean matches(Metadata metadata) {
                return true;
            }

            @Override
            public String toString() {
                return "ALL";
            }
        };
    }

    /**
     * Always false filter (matches nothing)
     */
    public static Filter none() {
        return new Filter() {
            @Override
            public boolean matches(Metadata metadata) {
                return false;
            }

            @Override
            public String toString() {
                return "NONE";
            }
        };
    }
}
