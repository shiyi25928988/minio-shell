# MinIO Shell

A **MinIO file-isolation gateway** built on the lightweight [plinth](https://github.com/) web framework (Guice 7 + Jetty 12): adds **user authentication + per-user isolated buckets + web file management + an S3-compatible endpoint** on top of MinIO.

No Spring Boot — pure Java 21, single fat-jar deployment.

## Features

- **User auth**: sa-token session management, register/login/logout, roles (admin/user)
- **Per-user file isolation**: each user owns a dedicated MinIO bucket (`user-<id>`); enforced at the app layer — normal users can only touch their own bucket
- **Web file manager**: j2html + Materialize CSS — browse/upload/download/delete/new-folder with breadcrumb navigation
- **S3-compatible endpoint**: `/s3/*` re-signing proxy; S3 clients (mc / aws-cli) connect directly; per-user access key
- **File sharing**: generate public share links with optional password / expiry / download-count limit; manage & revoke at `/page/shares`
- **Admin sees all**: admins can browse any user's files
- **HTTPS self-signed cert**: auto-generated on startup by IP/domain (Bouncy Castle)
- **Embedded DB**: H2 file mode — no external database required

## Quick start

### Prerequisite: a reachable MinIO

```bash
docker run -d --name minio -p 9000:9000 -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin minio/minio server /data
```

The app uses these admin credentials for all user-bucket operations (`minio.accessKey` / `minio.secretKey`).

### Run locally

```bash
mvn package -DskipTests
java -Dminio.endpoint=http://127.0.0.1:9000 \
     -Dminio.accessKey=minioadmin -Dminio.secretKey=minioadmin \
     -jar target/plinth-jre-21.jar
```

Open `http://localhost:443/page/login`; default account `admin` / `admin` (auto-created on first start).
> `server.ssl.enabled=false` (HTTP) by default; for HTTPS set `server.ssl.enabled=true` and `server.ssl.host`.

### Run with Docker

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

## Configuration

All settings can be overridden via env vars (Docker) or `-D` flags over `application.properties`:

| Env var | Property | Default | Description |
|---|---|---|---|
| `SERVER_PORT` | server.port | 443 | Service port |
| `SERVER_SSL_ENABLED` | server.ssl.enabled | false | Enable HTTPS |
| `SERVER_SSL_HOST` | server.ssl.host | localhost | Cert SAN (IP or domain) |
| `SERVER_SSL_CERT_DIR` | server.ssl.cert.dir | certs | Cert directory |
| `SERVER_SSL_KEYSTORE_PASSWORD` | server.ssl.keystore.password | plinth | Keystore password |
| `MINIO_ENDPOINT` | minio.endpoint | http://127.0.0.1:9000 | Backend MinIO URL |
| `MINIO_ACCESS_KEY` | minio.accessKey | minioadmin | MinIO admin access key |
| `MINIO_SECRET_KEY` | minio.secretKey | minioadmin | MinIO admin secret key |
| `MINIO_REGION` | minio.region | (empty) | MinIO region (empty -> us-east-1) |
| `MINIO_BUCKET_PREFIX` | minio.bucketPrefix | user- | Bucket-name prefix; bucket = prefix+id |
| `S3_EXTERNAL_ENDPOINT` | s3.external.endpoint | http://localhost:443/s3 | S3 endpoint shown to users |
| `ADMIN_DEFAULT_USERNAME` | admin.default.username | admin | Default admin username |
| `ADMIN_DEFAULT_PASSWORD` | admin.default.password | admin | Default admin password |
| `TOKEN_EXPIRE` | token.expire | 86400 | Token TTL (seconds) |
| `JDBC_DRIVER` | JDBC.driver | org.h2.Driver | DB driver |
| `JDBC_URL` | JDBC.url | jdbc:h2:file:./data/minio_shell;... | DB url |
| `JDBC_USERNAME` / `JDBC_PASSWORD` | JDBC.username / JDBC.password | sa / (empty) | DB credentials |
| `MYBATIS_MAPPER_SCAN` | mybatis.mapper.scan | yi.shi.plinth.db.mapper | Mapper scan package |

## Architecture

```
User ──┬── Web browser ──> app (443)
       │                    ├─ /page/*     admin UI (j2html)
       │                    ├─ /user/*     user API (sa-token)
       │                    ├─ /file/*     file API (MinIO SDK)
       │                    └─ /META-INF/*  static resources (webjars)
       │
       └── mc / aws-cli ─> /s3/*  re-signing proxy (SigV4)
                                ├─ access key -> user + verify signature (user secret)
                                ├─ isolation check (bucket == user's bucket)
                                └─ re-sign with admin creds -> MinIO
```

### Isolation model
- One bucket per user (`user-<id>`), created on registration.
- **Web**: `FileApi` pins the bucket to the logged-in user; admins may pass `bucket=` for others.
- **S3**: `MinioProxyServlet` resolves the user from the access key and requires the requested bucket to equal that user's bucket.
- MinIO only ever sees the admin credentials; isolation is enforced at the gateway.

### S3-compatible endpoint (/s3/*)
Each user obtains their access key / secret / bucket / endpoint on the **Profile page**. Client config:

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

**Limitation**: `STREAMING-AWS4-HMAC-SHA256-PAYLOAD` (aws-chunked signed uploads) is not supported. For large files use unsigned payload:
- mc does this by default;
- aws-cli: `aws configure set default.s3.payload_signing_enabled false`.

## Build

```bash
mvn clean package -DskipTests
```

Produces `target/plinth-jre-21.jar` (executable fat jar).

## License

MIT
