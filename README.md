<div align="center">
  <h1>MinIO Shell</h1>
  <p><b>MinIO 文件隔离网关</b> — 多用户认证 · 每用户独立存储 · Web 文件管理 · 文件分享 · S3 客户端直连</p>
  <br>
  <img alt="License" src="https://img.shields.io/badge/license-MIT-blue.svg">
  <img alt="Java" src="https://img.shields.io/badge/Java-21-orange.svg">
  <img alt="MinIO SDK" src="https://img.shields.io/badge/MinIO%20SDK-9.0-red.svg">
  <img alt="Jetty" src="https://img.shields.io/badge/Jetty-12.1-green.svg">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-No-purple.svg">
</div>

<p align="center">
🇺🇸 <a href="./README.en.md">English</a> | 🇨🇳 <a href="./README.md">简体中文</a>
</p>

## 目录

- [简介](#简介)
- [功能特性](#功能特性)
- [快速开始](#快速开始)
- [使用指南](#使用指南)
- [配置](#配置)
- [常见问题](#常见问题)
- [构建](#构建)
- [许可](#许可)

## 简介

MinIO Shell 是一个轻量级 **MinIO 文件隔离网关**。在单个 MinIO 之上叠加用户认证、每用户独立存储空间、Web 文件管理、文件分享与 S3 客户端直连,适合多人共享一个 MinIO 后端、但彼此文件需要完全隔离的场景。

- 单 fat jar 部署,内置嵌入式 H2 数据库,无需外部数据库。
- 启动时自动生成 HTTPS 自签名证书(可配置域名/IP)。
- 不使用 Spring Boot,基于 Guice 7 + Jetty 12,纯 Java 21。

## 功能特性

- **多用户隔离** — 每个用户独占一个 MinIO 桶(`user-<id>`),互相看不到对方的文件,应用层强制隔离。
- **Web 文件管理** — 浏览/上传/下载/删除/建文件夹/**右键在线重命名**(文件和文件夹均可,分享链接自动迁移);支持拖放与 **Ctrl+V 粘贴上传**(文件直传、截图自动命名)、上传可手动中断、批量下载/删除、按名称/大小/上传时间排序(默认最新在前)、按名搜索。
- **在线预览** — 图片、视频、音频、PDF 浏览器内直接打开;txt/md/json/代码等文本类在弹窗中展示;不支持预览的类型才走下载。
- **磁盘用量** — 首页顶部展示 MinIO 集群总容量/已用/剩余与使用率进度条(经 minio-admin 查询)。
- **文件分享** — 为任意文件生成公开链接,可设密码 / 有效期 / 下载次数,凭链接即可下载(无需登录);重命名文件/文件夹后旧链接继续有效。
- **S3 客户端直连** — 用 mc / aws-cli 配上自己的 access key 直连,像用 S3 一样传文件(每用户独立 key,仅限自己的空间)。
- **用户管理** — 管理员可查看所有用户、重置密码、浏览任意用户文件。
- **部署友好** — 单 fat jar 或 Docker;`docker-compose.yml` 已把嵌入式 H2 数据库文件绑定挂载到宿主目录,容器重建数据不丢。
- **HTTPS** — 启动时按配置的 IP/域名自动生成自签名证书。

## 快速开始

### 1. 准备一个 MinIO

```bash
docker run -d --name minio -p 9000:9000 \
  -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data
```

### 2. 启动 MinIO Shell

<details>
<summary>本地运行</summary>

```bash
mvn package -DskipTests
java -Dminio.endpoint=http://127.0.0.1:9000 \
     -Dminio.accessKey=minioadmin -Dminio.secretKey=minioadmin \
     -jar target/plinth-jre-21.jar
```
</details>

<details>
<summary>Docker Compose 运行(推荐)</summary>

```bash
mvn package -DskipTests
docker compose up -d --build
```

所有环境变量集中在 `docker-compose.yml`,H2 数据库文件绑定挂载到宿主 `./data`(证书在 `./certs`),容器重建/升级后数据仍在。容器内 MinIO 地址默认走 `host.docker.internal:9000`(MinIO 装在宿主机的场景),按实际情况修改。
</details>

<details>
<summary>Docker 直接运行</summary>

```bash
mvn package -DskipTests
docker build -t minio-shell .
docker run -d --name minio-shell -p 8080:80 \
  -v "$PWD/data:/app/data" -v "$PWD/certs:/app/certs" \
  -e MINIO_ENDPOINT=http://host.docker.internal:9000 \
  -e MINIO_ACCESS_KEY=minioadmin -e MINIO_SECRET_KEY=minioadmin \
  minio-shell
```
</details>

### 3. 登录

访问 `http://<host>:8080/page/login`(本地直跑端口同为 8080,见 `application.properties` 的 `server.port`;Docker 映射见上方命令),默认管理员在首次启动时按 `admin.default.password` / `ADMIN_DEFAULT_PASSWORD` 创建(仓库默认配置为 `admin` / `admin123`)。

> [!NOTE]
> 默认 `server.ssl.enabled=false`(HTTP)。如需 HTTPS,设 `server.ssl.enabled=true` 并配置 `server.ssl.host`。

## 使用指南

### 文件管理(首页 `/`)

| 操作 | 说明 |
|---|---|
| 浏览 | 点文件夹进入,面包屑导航返回上级;列表显示大小与上传时间 |
| 上传 | "Upload" 选文件、**拖放到页面**,或 **Ctrl+V 粘贴**(资源管理器复制的文件直传;截图自动命名为 `screenshot-时间戳.png`;纯文本忽略)。多文件并行上传,每文件独立进度条,可点 ✕ **手动中断** |
| 排序 | 右上 "Sort by" 选择 名称 / 大小 / 上传时间,↑/↓ 切换升降序;默认**上传时间倒序**(最新在前),文件夹始终置顶 |
| 批量操作 | 勾选行首复选框(或 "Select all"),批量下载 / 批量删除 |
| 下载 | 点下载图标下载;点文件名按类型处理(见下) |
| 在线预览 | 图片(jpg/png/gif/webp/svg/bmp/ico 等)、视频(mp4/webm/mov 等)、音频(mp3/wav/flac/m4a 等)、**PDF** 浏览器内打开;txt/md/log/csv/json/xml/yml/代码等**文本类**在弹窗中展示(超 2MB 转下载) |
| 重命名 | **右键点文件名 → Rename** 行内修改,Enter/失焦确认、Esc 取消;文件和文件夹都支持(文件夹为递归迁移),已生成的分享链接自动指向新名字 |
| 建文件夹 | 点 "New Folder",输入名称 |
| 删除 | 点删除图标(或批量删除),确认后删除;非空文件夹不能删;删除文件会自动清除它的所有分享链接 |
| 搜索 | 顶部搜索框,按文件名递归搜索整个空间(只搜文件,不搜文件夹) |
| 磁盘用量 | 顶部卡片显示 MinIO 集群已用/总容量与剩余空间,上传后自动刷新 |

### 文件分享

1. 文件行点链接(分享)图标生成分享链接,可设:**密码**、**有效期**(1/7/30 天/永久)、**最大下载次数**。
2. 链接 `/share/view?token=xxx` 可发给任何人,无需登录即可下载(设了密码则需输入)。
3. 在 "我的分享" 页(`/page/shares`)查看 / 复制 / 撤销自己的分享,查看下载次数。

### S3 客户端直连(mc / aws-cli)

在 **Profile 页**(`/page/profile`)获取自己的 Endpoint / Access Key / Secret / Bucket,然后:

```bash
# mc (MinIO Client)
mc alias set myapp http://<host>/s3 <ACCESS_KEY> <SECRET> --api S3v4
mc ls myapp/user-1/
mc cp ./file.txt myapp/user-1/
```

```bash
# aws-cli(path-style)
aws configure set default.s3.addressing_style path
aws --endpoint-url http://<host>/s3 s3 ls s3://user-1/
```

> [!IMPORTANT]
> `S3_EXTERNAL_ENDPOINT` 要配成外部可访问的地址(见[配置](#配置))。

### 个人资料(`/page/profile`)

- 查看账号信息
- **修改密码**:填当前密码 + 新密码 + 确认
- **S3 凭据**:查看 / 复制 access key/secret,重新生成(旧 key 立即失效)
- 客户端配置示例(mc/aws-cli)可直接复制

### 用户管理(`/page/users`,仅 admin)

- 查看所有用户列表
- **重置密码**:点 "Reset Password",该用户密码重置为 `123456`
- **查看文件**:点 "Files" 浏览该用户的存储空间

## 配置

所有配置可通过环境变量(Docker)或 `-D` 参数覆盖 `application.properties`:

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | 80(容器);本地配置为 8080 | 服务端口 |
| `SERVER_IDLE_TIMEOUT` | 300000 | HTTP 连接空闲超时(毫秒),大文件慢速下载超过该时长无字节往来才会断开;Jetty 默认仅 30s |
| `SERVER_SSL_ENABLED` | false | 是否启用 HTTPS |
| `SERVER_SSL_HOST` | localhost | 证书 SAN(IP 或域名) |
| `SERVER_SSL_CERT_DIR` | /app/certs(容器) | 证书目录 |
| `SERVER_SSL_KEYSTORE_PASSWORD` | plinth | keystore 密码 |
| `MINIO_ENDPOINT` | http://127.0.0.1:9000 | 后端 MinIO 地址 |
| `MINIO_ACCESS_KEY` | minioadmin | MinIO 管理员 access key |
| `MINIO_SECRET_KEY` | minioadmin | MinIO 管理员 secret key |
| `MINIO_REGION` | (空) | MinIO region(空 = us-east-1) |
| `MINIO_BUCKET_PREFIX` | user- | 用户桶名前缀,桶名 = 前缀 + 用户 id |
| `S3_EXTERNAL_ENDPOINT` | http://127.0.0.1/s3 | 展示给用户的 S3 端点(填外部可访问地址) |
| `ADMIN_DEFAULT_USERNAME` | admin | 默认管理员用户名 |
| `ADMIN_DEFAULT_PASSWORD` | admin(仅 entrypoint 兜底) | 默认管理员密码(仅首次创建生效;compose 与仓库 `application.properties` 中为 `admin123`) |
| `TOKEN_EXPIRE` | 86400 | 登录 token 过期(秒) |
| `JDBC_URL` | jdbc:h2:file:/app/data/minio_shell;...(容器) | 数据库连接(默认嵌入式 H2) |

> [!NOTE]
> 完整变量清单及默认值见 `docker-compose.yml` 与 `entrypoint.sh`;裸 `docker run` 不传任何环境变量也能用 entrypoint 内置默认值启动。
>
> `S3_EXTERNAL_ENDPOINT` 只在 Profile 页展示给用户(告诉用 mc/aws-cli 填什么),不影响实际路由。要填成外部可访问的地址,例如 `http://your-host:8080/s3`。
>
> 数据持久化:容器内 H2 文件位于 `/app/data/minio_shell.mv.db`,compose 已绑定挂载到宿主 `./data`;证书在 `/app/certs`。

## 常见问题

<details>
<summary><b>上传大文件失败 / ERR_CONNECTION_RESET</b></summary>

不是前端超时(前端不设超时)。通常是前面有 nginx 反代的默认限制:

```nginx
client_max_body_size 0;          # 不限制上传大小
proxy_read_timeout 3600s;        # 代理读取超时
proxy_send_timeout 3600s;
proxy_request_buffering off;     # 流式转发大文件
```
</details>

<details>
<summary><b>下载大文件中途断开、浏览器重新下载</b></summary>

Jetty 连接器默认 30 秒空闲超时:下载中客户端太慢/暂停写阻塞,30 秒内没有字节往来就会被掐断(日志 `Idle timeout expired: 30000/30000 ms`)。已新增 `SERVER_IDLE_TIMEOUT`(默认 300000ms = 5 分钟),下载在缓慢推进就不会断;经反代时同样要调大反代的 read/send 超时。
</details>

<details>
<summary><b>S3 客户端上传大文件失败</b></summary>

不支持 `STREAMING-AWS4-HMAC-SHA256-PAYLOAD`(aws-chunked 分块签名)。mc 默认 OK;aws-cli 执行:

```bash
aws configure set default.s3.payload_signing_enabled false
```
</details>

<details>
<summary><b>中文文件名下载乱码</b></summary>

已用 RFC 5987 编码(`filename*=UTF-8''...`),主流浏览器都支持,不会乱码。
</details>

<details>
<summary><b>忘记 admin 密码</b></summary>

`ADMIN_DEFAULT_PASSWORD` 只在首次创建 admin 时生效。已存在的 admin 密码无法用配置改回,需删除 H2 数据文件(`./data/minio_shell.mv.db`)重启重新初始化,或用另一个管理员重置。
</details>

## 构建

```bash
mvn clean package -DskipTests
```

生成 `target/plinth-jre-21.jar`(可执行 fat jar,需 Java 21)。

## 许可

[MIT](./LICENSE)
