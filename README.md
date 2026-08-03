# MinIO Shell

基于自研轻量 Web 框架 [plinth](https://github.com/) (Guice 7 + Jetty 12) 构建的 **MinIO 文件隔离网关**：为 MinIO 提供**用户认证 + 每用户独立桶 + Web 文件管理 + S3 兼容端点**。

不使用 Spring Boot，纯 Java 21，单 fat jar 部署。

## 功能

- **用户认证**：sa-token 会话管理，注册/登录/登出，角色控制（admin/user）
- **每用户文件隔离**：每个用户独占一个 MinIO 桶（`user-<id>`），应用层强制隔离，普通用户只能访问自己的桶
- **Web 文件管理**：j2html + Materialize CSS，浏览/上传/下载/删除/建文件夹，面包屑导航
- **S3 兼容端点**：`/s3/*` 重签名代理，mc / aws-cli 等 S3 客户端可直连，每用户独立 access key
- **文件分享**：为文件生成公开分享链接，支持可选密码 / 有效期 / 下载次数限制；`/page/shares` 管理与撤销
- **admin 全局可见**：admin 可浏览任意用户的文件
- **HTTPS 自签名证书**：启动时按 IP/域名自动生成，Bouncy Castle 实现
- **嵌入式数据库**：H2 文件模式，无需外部数据库

## 快速开始

### 前置：一个可访问的 MinIO

```bash
docker run -d --name minio -p 9000:9000 -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin minio/minio server /data
```

应用用此管理员凭据操作所有用户桶（`minio.accessKey` / `minio.secretKey`）。

### 本地运行

```bash
mvn package -DskipTests
java -Dminio.endpoint=http://127.0.0.1:9000 \
     -Dminio.accessKey=minioadmin -Dminio.secretKey=minioadmin \
     -jar target/plinth-jre-21.jar
```

访问 `http://localhost:443/page/login`，默认账号 `admin` / `admin`（首次启动自动创建）。
> 默认 `server.ssl.enabled=false`（HTTP）；如需 HTTPS 设 `server.ssl.enabled=true` 并配置 `server.ssl.host`。

### Docker 运行

```bash
mvn package -DskipTests
docker build -t minio-shell .

docker run -d --name minio-shell \
  -p 443:443 \
  -v ms-data:/app/data \
  -v ms-certs:/app/certs \
  -e SERVER_SSL_HOST=minio.example.com \
  -e MINIO_ENDPOINT=http://minio:9000 \
  -e MINIO_ACCESS_KEY=minioadmin \
  -e MINIO_SECRET_KEY=minioadmin \
  minio-shell
```

## 配置

所有配置均可通过环境变量（Docker）或 `-D` 参数覆盖 `application.properties`：

| 环境变量 | 配置项 | 默认值 | 说明 |
|---|---|---|---|
| `SERVER_PORT` | server.port | 443 | 服务端口 |
| `SERVER_SSL_ENABLED` | server.ssl.enabled | false | 是否启用 HTTPS |
| `SERVER_SSL_HOST` | server.ssl.host | localhost | 证书 SAN（IP 或域名） |
| `SERVER_SSL_CERT_DIR` | server.ssl.cert.dir | certs | 证书目录 |
| `SERVER_SSL_KEYSTORE_PASSWORD` | server.ssl.keystore.password | plinth | keystore 密码 |
| `MINIO_ENDPOINT` | minio.endpoint | http://127.0.0.1:9000 | 后端 MinIO 地址 |
| `MINIO_ACCESS_KEY` | minio.accessKey | minioadmin | MinIO 管理员 access key |
| `MINIO_SECRET_KEY` | minio.secretKey | minioadmin | MinIO 管理员 secret key |
| `MINIO_REGION` | minio.region | (空) | MinIO region（空时按 us-east-1） |
| `MINIO_BUCKET_PREFIX` | minio.bucketPrefix | user- | 用户桶名前缀，最终桶名 = 前缀+id |
| `S3_EXTERNAL_ENDPOINT` | s3.external.endpoint | http://localhost:443/s3 | 展示给用户的 S3 端点 |
| `ADMIN_DEFAULT_USERNAME` | admin.default.username | admin | 默认管理员用户名 |
| `ADMIN_DEFAULT_PASSWORD` | admin.default.password | admin | 默认管理员密码 |
| `TOKEN_EXPIRE` | token.expire | 86400 | token 过期时间（秒） |
| `JDBC_DRIVER` | JDBC.driver | org.h2.Driver | 数据库驱动 |
| `JDBC_URL` | JDBC.url | jdbc:h2:file:./data/minio_shell;... | 数据库连接 |
| `JDBC_USERNAME` / `JDBC_PASSWORD` | JDBC.username / JDBC.password | sa / (空) | 数据库账号 |
| `MYBATIS_MAPPER_SCAN` | mybatis.mapper.scan | yi.shi.plinth.db.mapper | Mapper 扫描包 |

## 架构

```
用户 ──┬── Web 浏览器 ──> 前端应用 (443)
       │                   ├─ /page/*     管理界面 (j2html)
       │                   ├─ /user/*     用户 API (sa-token 认证)
       │                   ├─ /file/*     文件 API (Web 数据面, MinIO SDK)
       │                   └─ /META-INF/*  静态资源 (webjars)
       │
       └── mc / aws-cli ─> /s3/*  重签名代理 (SigV4)
                                ├─ access key 查用户 + 验签 (用户 secret)
                                ├─ 隔离校验 (bucket == 用户桶)
                                └─ 管理员凭据重签名 -> MinIO
```

### 隔离模型
- 每用户独立桶 `user-<id>`，注册时创建。
- **Web 端**：`FileApi` 按当前登录用户固定桶；admin 可通过 `bucket` 参数指定他人桶。
- **S3 端**：`MinioProxyServlet` 按 access key 查到用户，校验请求的 bucket 必须等于该用户桶。
- MinIO 仅见到管理员凭据；隔离在网关层强制。

### S3 兼容端点（/s3/*）
每用户在 **Profile 页**获取自己的 access key / secret / bucket / endpoint。客户端配置示例：

```bash
# mc (MinIO Client)
mc alias set myapp http://<host>/s3 <ACCESS_KEY> <SECRET> --api S3v4
mc ls myapp/user-1/          # 列出文件
mc cp ./file.txt myapp/user-1/
```

```bash
# aws-cli（path-style）
aws configure set default.s3.addressing_style path
aws --endpoint-url http://<host>/s3 s3 ls s3://user-1/
```

**限制**：不支持 `STREAMING-AWS4-HMAC-SHA256-PAYLOAD`（aws-chunked 分块签名上传）。大文件请用 unsigned payload：
- mc 默认即如此；
- aws-cli 执行 `aws configure set default.s3.payload_signing_enabled false`。

### 核心模块

| 模块 | 包 | 说明 |
|---|---|---|
| 框架核心 | yi.shi.plinth | HTTP 路由、DI(Guice)、返回类型(HTML/JSON/BINARY)、Jetty 启动 |
| 用户管理 | yi.shi.plinth.user | 注册/登录/CRUD、PasswordEncoder、MinIO 凭据签发 |
| MinIO 数据面 | yi.shi.plinth.minio | MinioService（MinIO SDK：桶/对象操作） |
| 文件 API | yi.shi.plinth.file | FileApi（list/upload/download/delete/mkdir） |
| S3 代理 | yi.shi.plinth.proxy | MinioProxyServlet（/s3/* SigV4 重签名） |
| SigV4 | yi.shi.plinth.auth | SigV4Util（sign/verify）、AuthHelper、RoleStpInterface |
| 数据库 | yi.shi.plinth.db | DataSourceModule、SchemaInitializer、UserMapper |
| 证书 | yi.shi.plinth.cert | CertificateGenerator（Bouncy Castle 自签名） |
| 前端 | yi.shi.plinth.view | Page 基类、布局、页面、资源服务 |

### 页面

| 页面 | 路径 | 功能 |
|---|---|---|
| 登录/注册 | /page/login | 登录表单 + 注册切换 |
| 文件管理 | / | 当前用户桶的文件浏览器（admin 可 `?bucket=` 查看他人） |
| 我的分享 | /page/shares | 当前用户的分享列表，复制链接 / 撤销 |
| 分享访问 | /share/view?token= | 公开页（无需登录），凭 token 下载分享文件 |
| 用户管理 | /page/users | 用户列表 + "查看文件"（admin） |
| 个人资料 | /page/profile | 账号信息 + S3 凭据 + 客户端配置 + 重新生成密钥 |
| 404 | /page/404 | 未找到页面 |

## 技术栈

- Java 21、Guice 7、Jetty 12 ee10、sa-token 1.44
- MinIO Java SDK 8.5（数据面）
- MyBatis 3.5 + mybatis-guice 4、H2 2.2
- Bouncy Castle 1.78（证书）、j2html 1.6、Materialize CSS 1.0 + jQuery 3.7（webjars）
- Lombok、Jackson 3、Caffeine（本地缓存）

## 构建

```bash
mvn clean package -DskipTests
```

生成 `target/plinth-jre-21.jar`（可执行 fat jar）。

## 许可

MIT
