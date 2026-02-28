package com.csyangchsh.demo.vd.server;

import com.csyangchsh.demo.vd.api.VectorDB;
import com.csyangchsh.demo.vd.model.DistanceType;
import com.csyangchsh.demo.vd.model.Filter;
import com.csyangchsh.demo.vd.model.Metadata;
import com.csyangchsh.demo.vd.model.SearchResult;
import com.csyangchsh.demo.vd.model.Vector;
import com.csyangchsh.demo.vd.server.model.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.StreamSupport;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

/**
 * Netty-based HTTP server for VectorDB
 * Provides RESTful API endpoints with clean request/response models
 */
public class NettyHttpServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyHttpServer.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final VectorDB database;
    private final MetricsCollector metrics;
    private final ServerConfig config;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyHttpServer(VectorDB database, MetricsCollector metrics, ServerConfig config) {
        this.database = database;
        this.metrics = metrics;
        this.config = config;
    }

    /**
     * Start the HTTP server
     */
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(config.getServer().getThreads());

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(new HttpServerCodec());
                     // Max request size: 100MB (configured in config.yaml)
                     int maxRequestSize = config.getServer().getMaxRequestSize() * 1024 * 1024;
                     p.addLast(new HttpObjectAggregator(maxRequestSize));
                     p.addLast(new ApiHandler());
                 }
             })
             .option(ChannelOption.SO_BACKLOG, 1024)
             .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(config.getServer().getHost(), config.getServer().getPort()).sync();
            serverChannel = f.channel();

            metrics.setServing(true);
            logger.info("HTTP server started on {}:{}", config.getServer().getHost(), config.getServer().getPort());

            serverChannel.closeFuture().sync();
        } finally {
            shutdown();
        }
    }

    /**
     * Stop the HTTP server
     */
    public void shutdown() {
        metrics.setServing(false);
        logger.info("HTTP server stopping...");

        try {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        } catch (InterruptedException e) {
            logger.error("Error closing server channel", e);
            Thread.currentThread().interrupt();
        } finally {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
        }
        logger.info("HTTP server stopped");
    }

    /**
     * API request handler
     */
    @ChannelHandler.Sharable
    private class ApiHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            long startTime = System.currentTimeMillis();
            boolean success = false;

            try {
                String path = request.uri().split("\\?")[0];
                HttpMethod method = request.method();

                FullHttpResponse response = switch (path) {
                    case "/", "/vectors.html" -> handleVectorsPage();
                    case "/health" -> handleHealth(method);
                    case "/metrics" -> handleMetrics(method);
                    case "/vectors" -> handleVectors(method, request);
                    case "/vectors/all" -> handleAllVectors();
                    case "/search" -> handleSearch(method, request);
                    default -> handleNotFound(path);
                };

                success = true;

                long latency = System.currentTimeMillis() - startTime;
                metrics.recordRequest(latency, success);

                ctx.writeAndFlush(response);
            } catch (Exception e) {
                logger.error("Error processing request", e);
                FullHttpResponse errorResponse = createErrorResponse(
                        INTERNAL_SERVER_ERROR,
                        ErrorResponse.internalError("Internal server error", e.getMessage())
                );
                ctx.writeAndFlush(errorResponse);
            }
        }

        private FullHttpResponse handleHealth(HttpMethod method) {
            if (!method.equals(HttpMethod.GET)) {
                return createErrorResponse(METHOD_NOT_ALLOWED,
                        ErrorResponse.methodNotAllowed("Only GET is allowed"));
            }

            MetricsCollector.HealthStatus health = metrics.getHealthStatus();
            HealthResponse response = HealthResponse.healthy(
                    health.totalVectors(),
                    health.activeVectors(),
                    health.errorRate()
            );

            return createJsonResponse(OK, response);
        }

        private FullHttpResponse handleMetrics(HttpMethod method) {
            if (!method.equals(HttpMethod.GET)) {
                return createErrorResponse(METHOD_NOT_ALLOWED,
                        ErrorResponse.methodNotAllowed("Only GET is allowed"));
            }

            metrics.updateDatabaseState(
                    database.size(),
                    database.getActiveCount(),
                    estimateMemoryUsage()
            );

            String metricsJson = metrics.getMetricsJson();
            MetricsResponse response = GSON.fromJson(metricsJson, MetricsResponse.class);

            return createJsonResponse(OK, response);
        }

        private FullHttpResponse handleVectorsPage() {
            try {
                String html = readResourceFile("/vectors.html");
                ByteBuf content = Unpooled.copiedBuffer(html, StandardCharsets.UTF_8);
                FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, content);
                response.headers()
                        .set(CONTENT_TYPE, "text/html; charset=UTF-8")
                        .set(CONTENT_LENGTH, content.readableBytes());
                return response;
            } catch (Exception e) {
                logger.error("Failed to load vectors page", e);
                return createErrorResponse(INTERNAL_SERVER_ERROR,
                        ErrorResponse.internalError("Failed to load page", e.getMessage()));
            }
        }

        private FullHttpResponse handleAllVectors() {
            try {
                // Get all vectors from the default collection
                var collection = database.getDefaultCollection();
                int dimension = database.getDimension();
                int total = database.size();
                int active = database.getActiveCount();

                // Collect all vectors
                List<AllVectorsResponse.VectorDetail> vectorDetails = new ArrayList<>();
                for (String id : collection.getAllIds()) {
                    Vector v = collection.get(id);
                    if (v != null) {
                        vectorDetails.add(new AllVectorsResponse.VectorDetail(
                                v.getId(),
                                v.getDimension(),
                                v.isDeleted(),
                                v.getText(),
                                v.getData(),
                                v.getMetadata() != null ? v.getMetadata().toJson() : null
                        ));
                    }
                }

                AllVectorsResponse response = new AllVectorsResponse(
                        total,
                        active,
                        dimension,
                        vectorDetails.toArray(new AllVectorsResponse.VectorDetail[0])
                );

                return createJsonResponse(OK, response);
            } catch (Exception e) {
                logger.error("Error getting all vectors", e);
                return createErrorResponse(INTERNAL_SERVER_ERROR,
                        ErrorResponse.internalError("Failed to get vectors", e.getMessage()));
            }
        }

        private FullHttpResponse handleVectors(HttpMethod method, FullHttpRequest request) {
            return switch (method.name()) {
                case "GET" -> handleListVectors();
                case "POST" -> handlePostVectors(request);
                case "DELETE" -> handleDeleteVectors(request);
                default -> createErrorResponse(METHOD_NOT_ALLOWED,
                        ErrorResponse.methodNotAllowed("Allowed methods: GET, POST, DELETE"));
            };
        }

        private FullHttpResponse handleListVectors() {
            VectorModels.VectorsListResponse response = new VectorModels.VectorsListResponse(
                    database.size(),
                    database.getActiveCount(),
                    database.getDimension()
            );
            return createJsonResponse(OK, response);
        }

        private FullHttpResponse handlePostVectors(FullHttpRequest request) {
            try {
                String body = request.content().toString(CharsetUtil.UTF_8);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                if (json.has("vectors")) {
                    return handleBatchInsert(json);
                } else if (json.has("vector")) {
                    return handleSingleInsert(json);
                } else {
                    return createErrorResponse(BAD_REQUEST,
                            ErrorResponse.badRequest("Missing 'vector' or 'vectors' field"));
                }
            } catch (com.google.gson.JsonSyntaxException e) {
                logger.error("JSON parse error: {}", e.getMessage(), e);
                return createErrorResponse(BAD_REQUEST,
                        ErrorResponse.validationError("Invalid JSON format", e.getMessage()));
            } catch (Exception e) {
                logger.error("Error inserting vectors", e);
                return createErrorResponse(BAD_REQUEST,
                        ErrorResponse.validationError("Invalid request", e.getMessage()));
            }
        }

        private FullHttpResponse handleSingleInsert(JsonObject json) {
            float[] vector = parseVector(json.get("vector").getAsJsonArray());
            String text = json.has("text") ? json.get("text").getAsString() : null;
            Metadata metadata = json.has("metadata")
                    ? parseMetadata(json.getAsJsonObject("metadata")) : null;

            String id = database.insert(vector, text, metadata);
            metrics.recordInsert();

            VectorModels.InsertResponse response = new VectorModels.InsertResponse(id, vector.length);
            return createJsonResponse(CREATED, response);
        }

        private FullHttpResponse handleBatchInsert(JsonObject json) {
            var vectorsArray = json.getAsJsonArray("vectors");
            List<String> ids = new ArrayList<>(vectorsArray.size());

            for (JsonElement element : vectorsArray) {
                JsonObject vectorObj = element.getAsJsonObject();
                // Support both "vector" and "data" field names for consistency
                JsonElement vectorElement = vectorObj.has("vector") ? vectorObj.get("vector") : vectorObj.get("data");
                if (vectorElement == null) {
                    throw new IllegalArgumentException("Each vector must have 'vector' or 'data' field");
                }
                float[] data = parseVector(vectorElement.getAsJsonArray());
                String text = vectorObj.has("text") ? vectorObj.get("text").getAsString() : null;
                Metadata metadata = vectorObj.has("metadata")
                        ? parseMetadata(vectorObj.getAsJsonObject("metadata")) : null;

                String id = database.insert(data, text, metadata);
                ids.add(id);
                metrics.recordInsert();
            }

            VectorModels.BatchInsertResponse response = new VectorModels.BatchInsertResponse(
                    ids.size(),
                    ids.toArray(new String[0])
            );
            return createJsonResponse(CREATED, response);
        }

        private FullHttpResponse handleDeleteVectors(FullHttpRequest request) {
            try {
                String body = request.content().toString(CharsetUtil.UTF_8);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                if (!json.has("ids")) {
                    return createErrorResponse(BAD_REQUEST,
                            ErrorResponse.badRequest("Missing 'ids' field"));
                }

                var idsArray = json.getAsJsonArray("ids");
                for (JsonElement idElement : idsArray) {
                    String id = idElement.getAsString();
                    database.delete(id);
                    metrics.recordDelete();
                }

                VectorModels.DeleteResponse response = new VectorModels.DeleteResponse(idsArray.size());
                return createJsonResponse(OK, response);
            } catch (Exception e) {
                logger.error("Error deleting vectors", e);
                return createErrorResponse(BAD_REQUEST,
                        ErrorResponse.validationError("Invalid request", e.getMessage()));
            }
        }

        private FullHttpResponse handleSearch(HttpMethod method, FullHttpRequest request) {
            if (!method.equals(HttpMethod.POST)) {
                return createErrorResponse(METHOD_NOT_ALLOWED,
                        ErrorResponse.methodNotAllowed("Only POST is allowed"));
            }

            try {
                String body = request.content().toString(CharsetUtil.UTF_8);
                SearchModels.SearchRequest searchRequest = GSON.fromJson(body, SearchModels.SearchRequest.class);

                float[] query = searchRequest.query();
                int k = searchRequest.k() != null ? searchRequest.k() : 10;
                DistanceType distanceType = DistanceType.valueOf(searchRequest.distanceType());

                // Parse filter if present
                Filter filter = null;
                if (searchRequest.filter() != null) {
                    filter = parseFilter(searchRequest.filter());
                }

                SearchResult[] results = database.search(query, k, distanceType, filter);
                metrics.recordSearch();

                SearchModels.SearchResultItem[] resultItems = Arrays.stream(results)
                        .map(r -> {
                            Vector v = database.get(r.getVectorId());
                            return new SearchModels.SearchResultItem(
                                    r.getVectorId(),
                                    r.getScore(),
                                    v != null ? v.getText() : null,
                                    v != null && v.getMetadata() != null ? v.getMetadata().toJson() : null
                            );
                        })
                        .toArray(SearchModels.SearchResultItem[]::new);

                SearchModels.SearchResponse response = new SearchModels.SearchResponse(
                        query.length,
                        k,
                        resultItems.length,
                        resultItems
                );

                return createJsonResponse(OK, response);
            } catch (Exception e) {
                logger.error("Error searching", e);
                return createErrorResponse(BAD_REQUEST,
                        ErrorResponse.validationError("Invalid search request", e.getMessage()));
            }
        }

        private FullHttpResponse handleNotFound(String path) {
            return createErrorResponse(NOT_FOUND, ErrorResponse.notFound("Path not found: " + path));
        }

        // ========== Helper methods ==========

        private String readResourceFile(String path) throws IOException {
            try (var in = getClass().getResourceAsStream(path)) {
                if (in == null) {
                    throw new IOException("Resource not found: " + path);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        private FullHttpResponse createJsonResponse(HttpResponseStatus status, Object response) {
            String json = GSON.toJson(response);
            ByteBuf content = Unpooled.copiedBuffer(json, StandardCharsets.UTF_8);

            FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, status, content);
            httpResponse.headers()
                    .set(CONTENT_TYPE, "application/json; charset=UTF-8")
                    .set(CONTENT_LENGTH, content.readableBytes());

            return httpResponse;
        }

        private FullHttpResponse createErrorResponse(HttpResponseStatus status, ErrorResponse error) {
            return createJsonResponse(status, error);
        }

        private float[] parseVector(JsonElement element) {
            var array = element.getAsJsonArray();
            float[] vector = new float[array.size()];
            for (int i = 0; i < array.size(); i++) {
                vector[i] = array.get(i).getAsFloat();
            }
            return vector;
        }

        private Metadata parseMetadata(JsonObject json) {
            Metadata metadata = new Metadata();
            for (String key : json.keySet()) {
                JsonElement value = json.get(key);
                if (value.isJsonPrimitive()) {
                    var primitive = value.getAsJsonPrimitive();
                    if (primitive.isString()) {
                        metadata.put(key, primitive.getAsString());
                    } else if (primitive.isBoolean()) {
                        metadata.put(key, primitive.getAsBoolean());
                    } else if (primitive.isNumber()) {
                        String numStr = primitive.getAsString();
                        if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                            metadata.put(key, primitive.getAsDouble());
                        } else {
                            long longValue = primitive.getAsLong();
                            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                                metadata.put(key, longValue);
                            } else {
                                metadata.put(key, primitive.getAsDouble());
                            }
                        }
                    }
                }
            }
            return metadata;
        }

        private Filter parseFilter(JsonObject json) {
            // Basic filter parsing - can be extended
            if (json.has("operator") && json.has("operands")) {
                String operator = json.get("operator").getAsString();
                var operandsArray = json.getAsJsonArray("operands");
                List<Filter> filters = new ArrayList<>();
                for (JsonElement elem : operandsArray) {
                    filters.add(parseFilter(elem.getAsJsonObject()));
                }
                return switch (operator) {
                    case "AND" -> Filter.and(filters.toArray(new Filter[0]));
                    case "OR" -> Filter.or(filters.toArray(new Filter[0]));
                    case "NOT" -> filters.isEmpty() ? Filter.none() : Filter.not(filters.get(0));
                    default -> Filter.all();
                };
            } else if (json.has("field") && json.has("operation")) {
                String field = json.get("field").getAsString();
                String operation = json.get("operation").getAsString();
                JsonElement value = json.get("value");

                return switch (operation) {
                    case "EQ" -> {
                        if (value.isJsonPrimitive()) {
                            var p = value.getAsJsonPrimitive();
                            if (p.isString()) yield Filter.eq(field, p.getAsString());
                            else if (p.isBoolean()) yield Filter.eq(field, p.getAsBoolean());
                            else if (p.isNumber()) yield Filter.eq(field, p.getAsLong());
                        }
                        yield Filter.eq(field, value.getAsString());
                    }
                    case "NE" -> Filter.ne(field, value.getAsString());
                    case "GT" -> Filter.gt(field, value.getAsDouble());
                    case "GTE" -> Filter.gte(field, value.getAsLong());
                    case "LT" -> Filter.lt(field, value.getAsDouble());
                    case "LTE" -> Filter.lte(field, value.getAsLong());
                    case "CONTAINS" -> Filter.contains(field, value.getAsString());
                    case "STARTS_WITH" -> Filter.startsWith(field, value.getAsString());
                    case "ENDS_WITH" -> Filter.endsWith(field, value.getAsString());
                    case "EXISTS" -> Filter.exists(field);
                    default -> Filter.all();
                };
            }
            return Filter.all();
        }

        private long estimateMemoryUsage() {
            return (long) database.getDimension() * 4L * database.size();
        }
    }
}
