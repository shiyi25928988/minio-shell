#!/bin/sh
# 将环境变量映射为 -D 系统属性(优先于 jar 内 application.properties)
# 默认值与 docker-compose.yml 的 environment 段一致,未设置时兜底
# 额外参数可通过 JAVA_OPTS 追加
exec java \
  -Dserver.port="${SERVER_PORT:-80}" \
  -Dserver.idleTimeout="${SERVER_IDLE_TIMEOUT:-300000}" \
  -Dserver.ssl.enabled="${SERVER_SSL_ENABLED:-false}" \
  -Dserver.ssl.host="${SERVER_SSL_HOST:-127.0.0.1}" \
  -Dserver.ssl.cert.dir="${SERVER_SSL_CERT_DIR:-/app/certs}" \
  -Dserver.ssl.keystore.password="${SERVER_SSL_KEYSTORE_PASSWORD:-plinth}" \
  -Dminio.endpoint="${MINIO_ENDPOINT:-http://127.0.0.1:9000}" \
  -Dminio.accessKey="${MINIO_ACCESS_KEY:-minioadmin}" \
  -Dminio.secretKey="${MINIO_SECRET_KEY:-minioadmin}" \
  -Dminio.region="${MINIO_REGION:-}" \
  -Dminio.bucketPrefix="${MINIO_BUCKET_PREFIX:-user-}" \
  -Ds3.external.endpoint="${S3_EXTERNAL_ENDPOINT:-http://127.0.0.1/s3}" \
  -Dadmin.default.username="${ADMIN_DEFAULT_USERNAME:-admin}" \
  -Dadmin.default.password="${ADMIN_DEFAULT_PASSWORD:-admin}" \
  -Dtoken.expire="${TOKEN_EXPIRE:-86400}" \
  -DJDBC.driver="${JDBC_DRIVER:-org.h2.Driver}" \
  -DJDBC.url="${JDBC_URL:-jdbc:h2:file:/app/data/minio_shell;MODE=MySQL;DB_CLOSE_DELAY=-1}" \
  -DJDBC.username="${JDBC_USERNAME:-sa}" \
  -DJDBC.password="${JDBC_PASSWORD:-}" \
  -Dmybatis.environment.id="${MYBATIS_ENVIRONMENT_ID:-development}" \
  -Dmybatis.mapper.scan="${MYBATIS_MAPPER_SCAN:-yi.shi.plinth.db.mapper}" \
  $JAVA_OPTS \
  -jar app.jar
