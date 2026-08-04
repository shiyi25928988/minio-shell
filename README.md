<div align="center">
  <h1>MinIO Shell</h1>
  <p><b>MinIO 文件隔离网关</b> — 多用户认证 · 每用户独立存储 · Web 文件管理 · 文件分享 · S3 客户端直连</p>
  <br>
  <img alt="License" src="https://img.shields.io/badge/license-MIT-blue.svg">
  <img alt="Java" src="https://img.shields.io/badge/Java-21-orange.svg">
  <img alt="MinIO SDK" src="https://img.shields.io/badge/MinIO%20SDK-8.5-red.svg">
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
- **Web 文件管理** — 浏览器中浏览/上传/下载/删除/建文件夹,支持拖放上传(每文件独立进度条)、图片预览、视频播放、按名搜索。
- **文件分享** — 为任意文件生成公开链接,可设密码 / 有效期 / 下载次数,凭链接即可下载(无需登录)。
- **S3 客户端直连** — 用 mc / aws-cli 配上自己的 access key 直连,像用 S3 一样传文件(每用户独立 key,仅限自己的空间)。
- **用户管理** — 管理员可查看所有用户、重置密码、浏览任意用户文件。
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
<summary>Docker 运行</summary>

```bash
mvn package -DskipTests
docker build -t minio-shell .
docker run -d --name minio-shell -p 443:443 \
  -v ms-data:/app/data -v ms-certs:/app/certs \
  -e MINIO_ENDPOINT=http://minio:9000 \
  -e MINIO_ACCESS_KEY=minioadmin -e MINIO_SECRET_KEY=minioadmin \
  minio-shell
```
</details>

### 3. 登录

访问 `http://<host>/page/login`,默认管理员 `admin` / `admin`(首次启动自动创建,可用 `ADMIN_DEFAULT_PASSWORD` 修改默认密码)。

> [!NOTE]
> 默认 `server.ssl.enabled=false`(HTTP)。如需 HTTPS,设 `server.ssl.enabled=true` 并配置 `server.ssl.host`。

## 使用指南

### 文件管理(首页 `/`)

| 操作 | 说明 |
|---|---|
| 浏览 | 点文件夹进入,面包屑导航返回上级 |
| 上传 | 点 "Upload" 选文件,或**直接拖放文件到页面**;多文件并行上传,每个文件独立进度条(百分比 + 已传/总量) |
| 下载 | 点 ⬇️ 图标下载;点文件名按类型处理(图片预览/视频播放/否则下载) |
| 预览 | 图片自动显示,视频自动播放(支持浏览器原生格式:mp4/webm/ogg) |
| 建文件夹 | 点 "New Folder",输入名称 |
| 删除 | 点 🗑️ 确认后删除;非空文件夹不能删;删除文件会自动清除它的所有分享链接 |
| 搜索 | 顶部搜索框,按文件名递归搜索整个空间(只搜文件,不搜文件夹) |

### 文件分享

1. 文件行点 🔗 生成分享链接,可设:**密码**、**有效期**(1/7/30 天/永久)、**最大下载次数**。
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
| `SERVER_PORT` | 443 | 服务端口 |
| `SERVER_SSL_ENABLED` | false | 是否启用 HTTPS |
| `SERVER_SSL_HOST` | localhost | 证书 SAN(IP 或域名) |
| `SERVER_SSL_CERT_DIR` | certs | 证书目录 |
| `SERVER_SSL_KEYSTORE_PASSWORD` | plinth | keystore 密码 |
| `MINIO_ENDPOINT` | http://127.0.0.1:9000 | 后端 MinIO 地址 |
| `MINIO_ACCESS_KEY` | minioadmin | MinIO 管理员 access key |
| `MINIO_SECRET_KEY` | minioadmin | MinIO 管理员 secret key |
| `MINIO_REGION` | (空) | MinIO region(空 = us-east-1) |
| `MINIO_BUCKET_PREFIX` | user- | 用户桶名前缀,桶名 = 前缀 + 用户 id |
| `S3_EXTERNAL_ENDPOINT` | http://localhost:443/s3 | 展示给用户的 S3 端点(填外部可访问地址) |
| `ADMIN_DEFAULT_USERNAME` | admin | 默认管理员用户名 |
| `ADMIN_DEFAULT_PASSWORD` | admin | 默认管理员密码(仅首次创建生效) |
| `TOKEN_EXPIRE` | 86400 | 登录 token 过期(秒) |
| `JDBC_URL` | jdbc:h2:file:./data/minio_shell;... | 数据库连接(默认嵌入式 H2) |

> [!NOTE]
> `S3_EXTERNAL_ENDPOINT` 只在 Profile 页展示给用户(告诉用 mc/aws-cli 填什么),不影响实际路由。要填成外部可访问的地址,例如 `http://your-host:8088/s3`。

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
