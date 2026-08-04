<div align="center">
  <h1>MinIO Shell</h1>
  <p><b>MinIO File-Isolation Gateway</b> - Multi-user auth · Per-user isolated storage · Web file manager · File sharing · Direct S3-client access</p>
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

## Table of Contents

- [Introduction](#introduction)
- [Features](#features)
- [Quick Start](#quick-start)
- [User Guide](#user-guide)
- [Configuration](#configuration)
- [FAQ](#faq)
- [Build](#build)
- [License](#license)

## Introduction

MinIO Shell is a lightweight **MinIO file-isolation gateway**. It layers user authentication, per-user isolated storage, a web file manager, file sharing, and direct S3-client access on top of a single MinIO instance - ideal when multiple people share one MinIO backend but need their files kept fully separate.

- Single fat-jar deployment with an embedded H2 database - no external database required.
- Auto-generates an HTTPS self-signed certificate on startup (configurable domain/IP).
- No Spring Boot - built on Guice 7 + Jetty 12, pure Java 21.

## Features

- **Multi-user isolation** - Each user gets a dedicated MinIO bucket (`user-<id>`); users cannot see each other's files, enforced at the app layer.
- **Web file manager** - Browse/upload/download/delete/new-folder in the browser, with drag-and-drop upload (per-file progress bar), image preview, video playback, and name search.
- **File sharing** - Generate public links for any file with optional password / expiry / download-count limit; anyone with the link can download (no login needed).
- **Direct S3-client access** - Connect mc / aws-cli with your own access key, just like S3 (per-user key, scoped to your own space).
- **User management** - Admins can view all users, reset passwords, and browse any user's files.
- **HTTPS** - Auto-generates a self-signed certificate for the configured IP/domain on startup.

## Quick Start

### 1. Prepare a MinIO

```bash
docker run -d --name minio -p 9000:9000 \
  -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data
```

### 2. Start MinIO Shell

<details>
<summary>Run locally</summary>

```bash
mvn package -DskipTests
java -Dminio.endpoint=http://127.0.0.1:9000 \
     -Dminio.accessKey=minioadmin -Dminio.secretKey=minioadmin \
     -jar target/plinth-jre-21.jar
```
</details>

<details>
<summary>Run with Docker</summary>

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

### 3. Log in

Open `http://<host>/page/login`. Default admin is `admin` / `admin` (auto-created on first start; change the default with `ADMIN_DEFAULT_PASSWORD`).

> [!NOTE]
> Default `server.ssl.enabled=false` (HTTP). For HTTPS, set `server.ssl.enabled=true` and configure `server.ssl.host`.

## User Guide

### File management (home `/`)

| Action | Description |
|---|---|
| Browse | Click a folder to enter; breadcrumb navigation to go back up |
| Upload | Click "Upload" to pick files, or **drag-and-drop files onto the page**; multiple files upload in parallel, each with its own progress bar (percent + sent/total) |
| Download | Click ⬇️ to download; clicking the file name acts by type (image preview / video play / otherwise download) |
| Preview | Images display inline; videos play inline (browser-native formats: mp4/webm/ogg) |
| New folder | Click "New Folder" and enter a name |
| Delete | Click 🗑️ and confirm; non-empty folders cannot be deleted; deleting a file also removes all its share links |
| Search | Top search box, recursive name search across your whole space (files only, not folders) |

### File sharing

1. Click 🔗 on a file row to generate a share link with optional: **password**, **expiry** (1/7/30 days / never), **max download count**.
2. The link `/share/view?token=xxx` can be sent to anyone; no login required to download (password prompted if set).
3. Manage your shares on the "My Shares" page (`/page/shares`): copy/revoke links, view download counts.

### S3 client access (mc / aws-cli)

Get your Endpoint / Access Key / Secret / Bucket on the **Profile page** (`/page/profile`), then:

```bash
# mc (MinIO Client)
mc alias set myapp http://<host>/s3 <ACCESS_KEY> <SECRET> --api S3v4
mc ls myapp/user-1/
mc cp ./file.txt myapp/user-1/
```

```bash
# aws-cli (path-style)
aws configure set default.s3.addressing_style path
aws --endpoint-url http://<host>/s3 s3 ls s3://user-1/
```

> [!IMPORTANT]
> Set `S3_EXTERNAL_ENDPOINT` to the externally reachable address (see [Configuration](#configuration)).

### Profile (`/page/profile`)

- View account info
- **Change password**: enter current password + new password + confirm
- **S3 credentials**: view/copy access key/secret, regenerate (old key stops working immediately)
- Copy-ready mc/aws-cli config snippets

### User management (`/page/users`, admin only)

- View all users
- **Reset password**: click "Reset Password" to reset a user's password to `123456`
- **View files**: click "Files" to browse that user's storage space

## Configuration

All settings can be overridden via env vars (Docker) or `-D` flags over `application.properties`:

| Env var | Default | Description |
|---|---|---|
| `SERVER_PORT` | 443 | Service port |
| `SERVER_SSL_ENABLED` | false | Enable HTTPS |
| `SERVER_SSL_HOST` | localhost | Certificate SAN (IP or domain) |
| `SERVER_SSL_CERT_DIR` | certs | Certificate directory |
| `SERVER_SSL_KEYSTORE_PASSWORD` | plinth | Keystore password |
| `MINIO_ENDPOINT` | http://127.0.0.1:9000 | Backend MinIO URL |
| `MINIO_ACCESS_KEY` | minioadmin | MinIO admin access key |
| `MINIO_SECRET_KEY` | minioadmin | MinIO admin secret key |
| `MINIO_REGION` | (empty) | MinIO region (empty = us-east-1) |
| `MINIO_BUCKET_PREFIX` | user- | Bucket-name prefix; bucket = prefix + user id |
| `S3_EXTERNAL_ENDPOINT` | http://localhost:443/s3 | S3 endpoint shown to users (set to externally reachable address) |
| `ADMIN_DEFAULT_USERNAME` | admin | Default admin username |
| `ADMIN_DEFAULT_PASSWORD` | admin | Default admin password (only on first creation) |
| `TOKEN_EXPIRE` | 86400 | Login token TTL (seconds) |
| `JDBC_URL` | jdbc:h2:file:./data/minio_shell;... | Database URL (default embedded H2) |

> [!NOTE]
> `S3_EXTERNAL_ENDPOINT` is only displayed to users on the Profile page (telling them what to put in mc/aws-cli); it does not affect actual routing. Set it to the externally reachable address, e.g. `http://your-host:8088/s3`.

## FAQ

<details>
<summary><b>Large upload fails / ERR_CONNECTION_RESET</b></summary>

Not a frontend timeout (the frontend sets no timeout). Usually an nginx reverse proxy in front with default limits:

```nginx
client_max_body_size 0;          # no upload size limit
proxy_read_timeout 3600s;        # proxy read timeout
proxy_send_timeout 3600s;
proxy_request_buffering off;     # stream large files
```
</details>

<details>
<summary><b>S3 client large upload fails</b></summary>

`STREAMING-AWS4-HMAC-SHA256-PAYLOAD` (aws-chunked signed uploads) is not supported. mc works by default; for aws-cli run:

```bash
aws configure set default.s3.payload_signing_enabled false
```
</details>

<details>
<summary><b>Chinese filenames garbled on download</b></summary>

Already handled with RFC 5987 encoding (`filename*=UTF-8''...`); all major browsers display them correctly.
</details>

<details>
<summary><b>Forgot admin password</b></summary>

`ADMIN_DEFAULT_PASSWORD` only takes effect on first admin creation. An existing admin's password cannot be reset via config - delete the H2 data file (`./data/minio_shell.mv.db`) and restart to reinitialize, or have another admin reset it.
</details>

## Build

```bash
mvn clean package -DskipTests
```

Produces `target/plinth-jre-21.jar` (executable fat jar; requires Java 21).

## License

[MIT](./LICENSE)
