# MinIO Shell

A MinIO file-isolation gateway: adds **user authentication, per-user isolated storage, web file management, file sharing, and direct S3-client access** on top of MinIO. Ideal when multiple people share one MinIO but need their files kept separate.

## What it does

- **Multi-user isolation**: each user gets their own storage space (dedicated bucket); users cannot see each other's files.
- **Web file manager**: browse/upload/download/delete/new-folder in the browser, with drag-and-drop upload (per-file progress), image preview, video playback, and name search.
- **File sharing**: generate public links for any file with optional password / expiry / download-count limit; anyone with the link can download (no login needed).
- **Direct S3-client access**: connect mc/aws-cli with your own access key, just like S3 (per-user key, scoped to your own space).
- **User management**: admins can view all users, reset passwords, and browse any user's files.
- **HTTPS**: auto-generates a self-signed certificate on startup (configurable domain/IP).

## Quick start

### 1. Prepare a MinIO

```bash
docker run -d --name minio -p 9000:9000 \
  -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data
```

### 2. Start MinIO Shell

Run locally:
```bash
mvn package -DskipTests
java -Dminio.endpoint=http://127.0.0.1:9000 \
     -Dminio.accessKey=minioadmin -Dminio.secretKey=minioadmin \
     -jar target/plinth-jre-21.jar
```

Run with Docker:
```bash
mvn package -DskipTests
docker build -t minio-shell .
docker run -d --name minio-shell -p 443:443 \
  -v ms-data:/app/data -v ms-certs:/app/certs \
  -e MINIO_ENDPOINT=http://minio:9000 \
  -e MINIO_ACCESS_KEY=minioadmin -e MINIO_SECRET_KEY=minioadmin \
  minio-shell
```

### 3. Log in

Open `http://<host>/page/login`. Default admin is `admin` / `admin` (auto-created on first start; change the default with `ADMIN_DEFAULT_PASSWORD`).

## User guide

### File management (home `/`)

- **Browse**: click a folder to enter; breadcrumb navigation to go back up.
- **Upload**: click "Upload" to pick files, or **drag-and-drop files onto the page**. Multiple files upload in parallel, each with its own progress bar (percent + sent/total).
- **Download**: click ⬇️ to download; clicking the file name acts by type (image preview / video play / otherwise download).
- **Preview**: images display inline; videos play inline (browser-native formats: mp4/webm/ogg).
- **New folder**: click "New Folder" and enter a name.
- **Delete**: click 🗑️ and confirm. Non-empty folders cannot be deleted; deleting a file also removes all its share links.
- **Search**: top search box, recursive name search across your whole space (files only, not folders).

### File sharing

- Click 🔗 on a file row to generate a share link with optional: password, expiry (1/7/30 days / never), max download count.
- The link `/share/view?token=xxx` can be sent to anyone; no login required to download (password prompted if set).
- Manage your shares on the "My Shares" page (`/page/shares`): copy/revoke links, view download counts.

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

> Set `S3_EXTERNAL_ENDPOINT` to the externally reachable address (see config table).

### Profile (`/page/profile`)

- View account info.
- **Change password**: enter current password + new password + confirm.
- **S3 credentials**: view/copy access key/secret, regenerate (old key stops working immediately).
- Copy-ready mc/aws-cli config snippets.

### User management (`/page/users`, admin only)

- View all users.
- **Reset password**: click "Reset Password" to reset a user's password to `123456`.
- **View files**: click "Files" to browse that user's storage space.

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

> `S3_EXTERNAL_ENDPOINT` is only displayed to users on the Profile page (telling them what to put in mc/aws-cli); it does not affect actual routing. Set it to the externally reachable address, e.g. `http://your-host:8088/s3`.

## FAQ

**Large upload fails / `ERR_CONNECTION_RESET`**
Not a frontend timeout (the frontend sets no timeout). Usually an nginx reverse proxy in front with default limits:
- `client_max_body_size` (default 1MB) too small -> set `client_max_body_size 0;`
- `proxy_read_timeout` (default 60s) too short -> set `proxy_read_timeout 3600s;`
- For large files add `proxy_request_buffering off;` (stream forwarding)

**S3 client large upload fails**
`STREAMING-AWS4-HMAC-SHA256-PAYLOAD` (aws-chunked signed uploads) is not supported. mc works by default; for aws-cli run `aws configure set default.s3.payload_signing_enabled false`.

**Chinese filenames garbled on download**
Already handled with RFC 5987 encoding (`filename*=UTF-8''...`); all major browsers display them correctly.

**Forgot admin password**
`ADMIN_DEFAULT_PASSWORD` only takes effect on first admin creation. An existing admin's password cannot be reset via config — delete the H2 data file (`./data/minio_shell.mv.db`) and restart to reinitialize, or have another admin reset it.

## Build

```bash
mvn clean package -DskipTests
```
Produces `target/plinth-jre-21.jar` (executable fat jar; requires Java 21).

## License

MIT
