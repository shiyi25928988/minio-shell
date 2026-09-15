<div align="center">
  <h1>MinIO Shell</h1>
  <p><b>MinIO File-Isolation Gateway</b> - Multi-user auth · Per-user isolated storage · Web file manager · File sharing · Direct S3-client access</p>
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
- **Web file manager** - Browse/upload/download/delete/new-folder/**right-click inline rename** (files and folders; share links migrate automatically). Supports drag-and-drop and **Ctrl+V paste upload** (copied files upload directly, screenshots are auto-named), manual upload cancel, batch download/delete, sorting by name/size/upload time (newest first by default), and name search.
- **Inline preview** - Images, videos, audio and PDFs open in the browser; text-like files (txt/md/json/code...) render in a dialog; only unsupported types fall back to download.
- **Disk usage** - A card on the home page shows the MinIO cluster's total/used/free space and utilization (queried via minio-admin).
- **File sharing** - Generate public links for any file with optional password / expiry / download-count limit; anyone with the link can download (no login needed). Existing links keep working after a rename.
- **Direct S3-client access** - Connect mc / aws-cli with your own access key, just like S3 (per-user key, scoped to your own space).
- **User management** - Admins can view all users, reset passwords, and browse any user's files.
- **Deployment-friendly** - Single fat jar or Docker; `docker-compose.yml` bind-mounts the embedded H2 database file to a host directory, so data survives container recreation.
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
<summary>Run with Docker Compose (recommended)</summary>

```bash
mvn package -DskipTests
docker compose up -d --build
```

All environment variables live in `docker-compose.yml`. The H2 database file is bind-mounted to host `./data` (certs to `./certs`), so data survives container recreation/upgrade. Inside the container MinIO defaults to `host.docker.internal:9000` (for a MinIO running on the Docker host) - adjust to your setup.
</details>

<details>
<summary>Run with plain Docker</summary>

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

### 3. Log in

Open `http://<host>:8080/page/login` (local runs also default to 8080 per `server.port` in `application.properties`; for Docker see the port mapping above). The admin is created on first start from `admin.default.password` / `ADMIN_DEFAULT_PASSWORD` (the repo defaults to `admin` / `admin123`).

> [!NOTE]
> Default `server.ssl.enabled=false` (HTTP). For HTTPS, set `server.ssl.enabled=true` and configure `server.ssl.host`.

## User Guide

### File management (home `/`)

| Action | Description |
|---|---|
| Browse | Click a folder to enter; breadcrumb navigation to go back up; the list shows size and upload time |
| Upload | "Upload" button, **drag-and-drop**, or **Ctrl+V paste** (files copied in Explorer upload directly; screenshots are auto-named `screenshot-<timestamp>.png`; plain text is ignored). Parallel uploads with a per-file progress bar and an ✕ button to **cancel** |
| Sort | Top-right "Sort by": name / size / upload time, with ↑/↓ direction toggle. Default is **upload time descending** (newest first); folders always stay on top |
| Batch | Check the row checkbox (or "Select all") for batch download / batch delete |
| Download | Click the download icon; clicking the file name acts by type (see below) |
| Inline preview | Images (jpg/png/gif/webp/svg/bmp/ico...), videos (mp4/webm/mov...), audio (mp3/wav/flac/m4a...), and **PDFs** open in-browser; text-like files (txt/md/log/csv/json/xml/yml/code...) render in a dialog (>2MB falls back to download) |
| Rename | **Right-click the file name → Rename** for inline editing; Enter/blur confirms, Esc cancels. Works for files and folders (folders migrate recursively), and existing share links automatically point at the new name |
| New folder | Click "New Folder" and enter a name |
| Delete | Click the delete icon (or batch delete) and confirm; non-empty folders cannot be deleted; deleting a file also removes all its share links |
| Search | Top search box, recursive name search across your whole space (files only, not folders) |
| Disk usage | The top card shows the MinIO cluster's used/total and free space; refreshes after uploads |

### File sharing

1. Click the link (share) icon on a file row to generate a share link with optional: **password**, **expiry** (1/7/30 days / never), **max download count**.
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
| `SERVER_PORT` | 80 (container); 8080 locally | Service port |
| `SERVER_IDLE_TIMEOUT` | 300000 | HTTP connection idle timeout (ms); a slow download is only cut after no bytes for this long (Jetty's own default is just 30s) |
| `SERVER_SSL_ENABLED` | false | Enable HTTPS |
| `SERVER_SSL_HOST` | localhost | Certificate SAN (IP or domain) |
| `SERVER_SSL_CERT_DIR` | /app/certs (container) | Certificate directory |
| `SERVER_SSL_KEYSTORE_PASSWORD` | plinth | Keystore password |
| `MINIO_ENDPOINT` | http://127.0.0.1:9000 | Backend MinIO URL |
| `MINIO_ACCESS_KEY` | minioadmin | MinIO admin access key |
| `MINIO_SECRET_KEY` | minioadmin | MinIO admin secret key |
| `MINIO_REGION` | (empty) | MinIO region (empty = us-east-1) |
| `MINIO_BUCKET_PREFIX` | user- | Bucket-name prefix; bucket = prefix + user id |
| `S3_EXTERNAL_ENDPOINT` | http://127.0.0.1/s3 | S3 endpoint shown to users (set to externally reachable address) |
| `ADMIN_DEFAULT_USERNAME` | admin | Default admin username |
| `ADMIN_DEFAULT_PASSWORD` | admin (entrypoint fallback only) | Default admin password (only on first creation; compose and the repo's `application.properties` use `admin123`) |
| `TOKEN_EXPIRE` | 86400 | Login token TTL (seconds) |
| `JDBC_URL` | jdbc:h2:file:/app/data/minio_shell;... (container) | Database URL (default embedded H2) |

> [!NOTE]
> See `docker-compose.yml` and `entrypoint.sh` for the full variable list and defaults; a plain `docker run` with no env vars still boots using the entrypoint defaults.
>
> `S3_EXTERNAL_ENDPOINT` is only displayed to users on the Profile page (telling them what to put in mc/aws-cli); it does not affect actual routing. Set it to the externally reachable address, e.g. `http://your-host:8080/s3`.
>
> Persistence: the in-container H2 file is `/app/data/minio_shell.mv.db`, bind-mounted to host `./data` by compose; certificates live in `/app/certs`.

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
<summary><b>Large download is cut off halfway and the browser restarts</b></summary>

The Jetty connector's default idle timeout is 30 seconds: if the client is too slow or writes block with no bytes exchanged for 30s, the connection is dropped (`Idle timeout expired: 30000/30000 ms` in the log). A new `SERVER_IDLE_TIMEOUT` (default 300000ms = 5 min) keeps a slowly progressing download alive; raise the reverse proxy's read/send timeouts as well.
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
