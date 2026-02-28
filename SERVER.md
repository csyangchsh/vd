# VectorDB Server 使用指南

向量数据库独立服务 - 基于HTTP REST API的高性能向量数据库服务。

## 目录

1. [快速开始](#快速开始)
2. [配置说明](#配置说明)
3. [API接口](#api接口)
4. [管理操作](#管理操作)
5. [监控指标](#监控指标)
6. [故障排查](#故障排查)

---

## 快速开始

### 启动服务器

**Linux/macOS:**
```bash
# 赋予执行权限
chmod +x start-server.sh

# 前台启动（默认配置）
./start-server.sh

# 后台启动
./start-server.sh --daemon

# 使用自定义配置
./start-server.sh --config /path/to/config.yaml

# 查看状态
./start-server.sh --status

# 停止服务器
./start-server.sh --stop
```

**Windows:**
```cmd
# 前台启动
start-server.bat

# 后台启动
start-server.bat --daemon

# 使用自定义配置
start-server.bat --config config.yaml

# 查看状态
start-server.bat --status

# 停止服务器
start-server.bat --stop
```

### 使用Java直接启动

```bash
# 编译项目
mvn clean package -DskipTests

# 启动服务器
java --enable-preview --add-modules=jdk.incubator.vector \
     -cp target/vector-db-1.0.0.jar \
     com.csyangchsh.demo.vd.server.VectorDBServer \
     --config config.yaml
```

---

## 架构说明

### Netty HTTP 服务器

VectorDB Server 使用 **Netty** 作为 HTTP 服务器框架，提供高性能的异步 I/O 能力：

**特性**:
- 基于 Netty 4.x 的事件驱动架构
- 非阻塞 I/O，高并发处理能力
- 自动连接池管理和资源复用
- 优雅的关闭机制

**目录结构**:
```
src/main/java/com/csyangchsh/demo/vd/server/
├── VectorDBServer.java      # 主服务器类
├── NettyHttpServer.java      # Netty HTTP 服务器实现
├── ServerConfig.java         # 配置管理
├── MetricsCollector.java     # 指标收集
└── model/                    # API 请求/响应模型
    ├── HealthResponse.java
    ├── MetricsResponse.java
    ├── VectorModels.java
    ├── SearchModels.java
    ├── PersistenceModels.java
    └── ErrorResponse.java
```

### API 模型

所有 API 使用类型安全的 Java Record 类作为请求和响应模型：

**响应模型**:
- `HealthResponse` - 健康检查响应
- `MetricsResponse` - 指标响应（包含 server, requests, operations, database）
- `InsertResponse` - 单个插入响应
- `BatchInsertResponse` - 批量插入响应
- `DeleteResponse` - 删除响应
- `SearchResponse` - 搜索响应
- `ErrorResponse` - 错误响应（含 code, error, details）

**请求模型**:
- `InsertVectorRequest` - 单个向量插入请求
- `BatchInsertRequest` - 批量插入请求
- `DeleteVectorsRequest` - 删除请求
- `SearchRequest` - 搜索请求（支持 metadata filter）
- `SaveRequest` / `LoadRequest` - 持久化请求

---

## 配置说明

### 配置文件结构 (config.yaml)

```yaml
server:
  host: "0.0.0.0"      # 绑定地址
  port: 8080            # 端口号
  threads: 16           # 工作线程数

database:
  dimension: 128        # 向量维度
  distanceType: "L2"    # 距离类型: L2, COSINE, INNER_PRODUCT
  indexType: "HNSW"     # 索引类型: FLAT, COMPACT_FLAT, HNSW, PQ
  indexPath: "./data/vectordb"  # 数据存储路径
  autoSave: true        # 自动保存
  autoSaveInterval: 300 # 自动保存间隔（秒）

hnsw:
  M: 16                 # HNSW最大连接数
  efConstruction: 200   # HNSW构建参数

logging:
  level: "INFO"         # 日志级别
```

### 配置参数详解

#### server 配置

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| host | string | 0.0.0.0 | 绑定地址，0.0.0.0表示所有接口 |
| port | int | 8080 | HTTP端口 |
| threads | int | 16 | 工作线程数，建议设置为CPU核心数 |

#### database 配置

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| dimension | int | 必填 | 向量维度，一旦设置不可更改 |
| distanceType | enum | L2 | 距离度量：L2/COSINE/INNER_PRODUCT |
| indexType | enum | HNSW | 索引类型：FLAT/COMPACT_FLAT/HNSW/PQ |
| indexPath | path | ./data/vectordb | 数据存储目录 |
| autoSave | bool | true | 是否自动保存 |
| autoSaveInterval | int | 300 | 自动保存间隔（秒） |

#### hnsw 配置（仅HNSW索引有效）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| M | int | 16 | 每节点最大连接数，推荐16 |
| efConstruction | int | 200 | 构建时的搜索宽度 |

---

## API接口

所有API返回JSON格式数据，基础URL为 `http://host:port`

### 0. 向量管理页面 (Web UI)

```
GET /vectors.html
```

访问基于Web的向量管理界面，提供可视化操作。

### 1. 健康检查

```
GET /health
```

**响应示例:**
```json
{
  "status": "HEALTHY",
  "serving": true,
  "total_vectors": 10000,
  "active_vectors": 9950,
  "error_rate": 0.0001
}
```

### 2. 监控指标

```
GET /metrics
```

**响应示例:**
```json
{
  "server": {
    "uptime_ms": 3600000,
    "uptime_seconds": 3600.0,
    "serving": true,
    "start_time": 1704067200000
  },
  "requests": {
    "total": 100000,
    "successful": 99990,
    "failed": 10,
    "avg_latency_ms": 5
  },
  "operations": {
    "insert": 10000,
    "search": 89990,
    "delete": 20
  },
  "database": {
    "total_vectors": 10000,
    "active_vectors": 9950,
    "memory_bytes": 5120000,
    "memory_mb": 4.88
  }
}
```

### 3. 向量操作

#### 3.1 获取向量列表信息

```
GET /vectors
```

**响应:**
```json
{
  "total": 10000,
  "active": 9950,
  "dimension": 128
}
```

#### 3.2 获取所有向量详情

```
GET /vectors/all
```

**响应:**
```json
{
  "total": 10000,
  "active": 9950,
  "dimension": 128,
  "vectors": [
    {
      "id": "0195eced-8000-7000-8000-000000000001",
      "dimension": 128,
      "deleted": false,
      "text": "Sample document text",
      "metadata": {"category": "news", "year": 2024}
    },
    ...
  ]
}
```

#### 3.3 插入向量

```
POST /vectors
Content-Type: application/json
```

**单个插入:**
```json
{
  "vector": [0.1, 0.2, 0.3, ...],
  "text": "Optional original text",
  "metadata": {"category": "news", "timestamp": 1704067200000},
  "payload": "base64_encoded_optional_data"
}
```

**批量插入:**
```json
{
  "vectors": [
    {"data": [0.1, 0.2, ...], "text": "...", "metadata": {...}},
    {"data": [0.3, 0.4, ...], "text": "...", "metadata": {...}}
  ]
}
```

**响应:**
```json
{
  "id": "0195eced-8000-7000-8000-000000000001",
  "dimension": 128
}
```

或批量插入:
```json
{
  "count": 100,
  "ids": ["0195eced-8000-7000-8000-000000000001", "0195eced-8000-7000-8000-000000000002", ...]
}
```

**注意**: 向量ID使用UUID v7格式 (RFC 9562)，具有时间排序特性，适合分布式系统。

#### 3.4 删除向量

```
POST /vectors
Content-Type: application/json
```

```json
{
  "ids": ["0195eced-8000-7000-8000-000000000001", "0195eced-8000-7000-8000-000000000002"]
}
```

**响应:**
```json
{
  "deleted": 2
}
```

### 3.5 向量搜索

```
POST /search
Content-Type: application/json
```

```json
{
  "query": [0.1, 0.2, 0.3, ...],
  "k": 10,
  "distanceType": "L2"
}
```

**响应:**
```json
{
  "query_dimension": 128,
  "k": 10,
  "result_count": 10,
  "results": [
    {"id": "0195eced-8000-7000-8000-000000000001", "score": 0.1234},
    {"id": "0195eced-8000-7000-8000-000000000002", "score": 0.2345},
    ...
  ]
}
```

## 管理操作

### 交互式控制台

前台模式启动后，可以使用以下命令：

```
> status   # 查看服务器状态
> metrics  # 查看详细指标
> stop     # 停止服务器
> help     # 显示帮助
> quit     # 退出控制台（服务器继续运行）
```

### 使用curl测试API

```bash
# 健康检查
curl http://localhost:8080/health

# 插入向量
curl -X POST http://localhost:8080/vectors \
  -H "Content-Type: application/json" \
  -d '{
    "vector": [0.1, 0.2, 0.3, 0.4, 0.5],
    "payload": "aGVsbG8="
  }'

# 搜索
curl -X POST http://localhost:8080/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": [0.1, 0.2, 0.3, 0.4, 0.5],
    "k": 5
  }'

# 获取指标
curl http://localhost:8080/metrics

# 保存数据库
curl -X POST http://localhost:8080/save \
  -H "Content-Type: application/json" \
  -d '{"path": "backup.bin"}'
```

### 使用Python客户端

```python
import requests
import json

class VectorDBClient:
    def __init__(self, host='localhost', port=8080):
        self.base_url = f'http://{host}:{port}'

    def insert(self, vector):
        response = requests.post(
            f'{self.base_url}/vectors',
            json={'vector': vector}
        )
        return response.json()

    def search(self, query, k=10):
        response = requests.post(
            f'{self.base_url}/search',
            json={'query': query, 'k': k}
        )
        return response.json()

    def get_metrics(self):
        response = requests.get(f'{self.base_url}/metrics')
        return response.json()

# 使用示例
client = VectorDBClient()
vector = [0.1] * 128
result = client.insert(vector)
print("Inserted ID:", result['id'])

search_results = client.search(vector, k=5)
print("Search results:", search_results)
```

---

## 监控指标

### 关键指标说明

| 指标                      | 说明     | 正常范围          |
|-------------------------|--------|---------------|
| uptime_seconds          | 服务运行时间 | -             |
| requests.total          | 总请求数   | -             |
| requests.failed         | 失败请求数  | 越低越好          |
| avg_latency_ms          | 平均延迟   | < 10ms (HNSW) |
| database.active_vectors | 活跃向量数  | -             |
| memory_mb               | 内存使用量  | 取决于数据量        |

### 性能调优建议

1. **线程数设置**: 建议设置为CPU核心数的1-2倍
2. **内存设置**: 根据数据量调整JVM堆内存
   ```
   -Xms2g -Xmx8g  # 8GB堆内存
   ```
3. **索引选择**:
   - < 10K向量: FLAT
   - 10K - 1M: HNSW
   - > 1M: HNSW 或 PQ

---

## 错误响应格式

所有 API 错误返回统一格式的错误响应：

```json
{
  "error": "错误描述信息",
  "code": "错误代码",
  "details": "详细错误信息（可选）"
}
```

**错误代码**:

| 代码                 | HTTP状态码 | 说明           |
|--------------------|---------|--------------|
| BAD_REQUEST        | 400     | 请求参数错误或格式不正确 |
| VALIDATION_ERROR   | 400     | 数据验证失败       |
| NOT_FOUND          | 404     | 资源不存在        |
| METHOD_NOT_ALLOWED | 405     | 不支持的HTTP方法   |
| INTERNAL_ERROR     | 500     | 服务器内部错误      |

**错误响应示例**:

```json
{
  "error": "Invalid vector dimension: expected 128, got 256",
  "code": "VALIDATION_ERROR",
  "details": "Query vector dimension must match database dimension"
}
```

---

## 故障排查

### 服务器无法启动

**问题**: 启动时提示 "Address already in use"

**解决**:
```bash
# 检查端口占用
lsof -i :8080  # Linux/macOS
netstat -ano | findstr 8080  # Windows

# 修改配置文件中的端口
```

### 内存不足

**问题**: java.lang.OutOfMemoryError

**解决**:
```bash
# 修改启动脚本中的JVM内存参数
JAVA_OPTS="-Xms4g -Xmx16g"
```

### 配置文件找不到

**问题**: Config file not found

**解决**:
```bash
# 检查配置文件路径
ls -la config.yaml

# 使用绝对路径启动
./start-server.sh --config /full/path/to/config.yaml
```

### 查看日志

**后台模式日志**:
```bash
tail -f vectordb.log
```

**前台模式**: 日志直接输出到控制台

---

## 部署建议

### 生产环境部署

1. **使用systemd管理 (Linux)**

创建 `/etc/systemd/system/vectordb.service`:

```ini
[Unit]
Description=VectorDB Server
After=network.target

[Service]
Type=simple
User=vectordb
WorkingDirectory=/opt/vectordb
ExecStart=/usr/bin/java --enable-preview --add-modules=jdk.incubator.vector \\
    -cp /opt/vectordb/vector-db.jar \\
    com.csyangchsh.demo.vd.server.VectorDBServer \\
    --config /etc/vectordb/config.yaml
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务:
```bash
sudo systemctl daemon-reload
sudo systemctl start vectordb
sudo systemctl enable vectordb  # 开机自启
sudo systemctl status vectordb
```

2. **使用Docker部署**

```dockerfile
FROM eclipse-temurin:25-jdk

WORKDIR /app
COPY target/vector-db-1.0.0.jar app.jar
COPY config.yaml config.yaml

EXPOSE 8080

ENTRYPOINT ["java", "--enable-preview", "--add-modules=jdk.incubator.vector", \
          "-jar", "app.jar", "--config", "config.yaml"]
```

```bash
docker build -t vectordb .
docker run -d -p 8080:8080 -v $(pwd)/data:/app/data vectordb
```

---

## 安全建议

1. **不要在生产环境使用默认端口**
2. **配置防火墙限制访问**
3. **使用反向代理 (Nginx) 添加认证**
4. **定期备份数据文件**
5. **监控日志文件大小**

---

## 更多信息

- 设计文档: [DESIGN.md](DESIGN.md)
