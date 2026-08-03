#!/bin/sh
# 将环境变量映射为 -D 系统属性(优先于 jar 内 application.properties)
# 额外参数可通过 JAVA_OPTS 追加
exec java \
  -Dserver.port="$SERVER_PORT" \
  -Dserver.ssl.enabled="$SERVER_SSL_ENABLED" \
  -Dserver.ssl.host="$SERVER_SSL_HOST" \
  -Dserver.ssl.cert.dir="$SERVER_SSL_CERT_DIR" \
  -Dserver.ssl.keystore.password="$SERVER_SSL_KEYSTORE_PASSWORD" \
  -Dminio.endpoint="$MINIO_ENDPOINT" \
  -Dminio.accessKey="$MINIO_ACCESS_KEY" \
  -Dminio.secretKey="$MINIO_SECRET_KEY" \
  -Dminio.region="$MINIO_REGION" \
  -Dminio.bucketPrefix="$MINIO_BUCKET_PREFIX" \
  -Ds3.external.endpoint="$S3_EXTERNAL_ENDPOINT" \
  -Dadmin.default.username="$ADMIN_DEFAULT_USERNAME" \
  -Dadmin.default.password="$ADMIN_DEFAULT_PASSWORD" \
  -Dtoken.expire="$TOKEN_EXPIRE" \
  -DJDBC.driver="$JDBC_DRIVER" \
  -DJDBC.url="$JDBC_URL" \
  -DJDBC.username="$JDBC_USERNAME" \
  -DJDBC.password="$JDBC_PASSWORD" \
  -Dmybatis.environment.id="$MYBATIS_ENVIRONMENT_ID" \
  -Dmybatis.mapper.scan="$MYBATIS_MAPPER_SCAN" \
  $JAVA_OPTS \
  -jar app.jar
