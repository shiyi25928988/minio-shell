# 运行阶段:JDK 21 JRE
FROM eclipse-temurin:21-jre

WORKDIR /app

# 复制 fat jar 与启动脚本(需先在宿主执行 mvn package)
COPY target/plinth-jre-21.jar app.jar
COPY entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

# 所有配置项默认值(均可通过 docker run -e 覆盖,entrypoint.sh 映射为 -D 优先于 jar 内 application.properties)
ENV SERVER_PORT=80
ENV SERVER_SSL_ENABLED=false
ENV SERVER_SSL_HOST=127.0.0.1
ENV SERVER_SSL_CERT_DIR=/app/certs
ENV SERVER_SSL_KEYSTORE_PASSWORD=plinth
ENV MINIO_ENDPOINT=http://127.0.0.1:9000
ENV MINIO_ACCESS_KEY=minioadmin
ENV MINIO_SECRET_KEY=minioadmin
ENV MINIO_REGION=
ENV MINIO_BUCKET_PREFIX=user-
ENV S3_EXTERNAL_ENDPOINT=http://127.0.0.1/s3
ENV ADMIN_DEFAULT_USERNAME=admin
ENV ADMIN_DEFAULT_PASSWORD=admin
ENV TOKEN_EXPIRE=86400
ENV JDBC_DRIVER=org.h2.Driver
ENV JDBC_URL="jdbc:h2:file:/app/data/minio_shell;MODE=MySQL;DB_CLOSE_DELAY=-1"
ENV JDBC_USERNAME=sa
ENV JDBC_PASSWORD=
ENV MYBATIS_ENVIRONMENT_ID=development
ENV MYBATIS_MAPPER_SCAN=yi.shi.plinth.db.mapper

# 持久化:H2 数据 + 证书
VOLUME ["/app/data", "/app/certs"]

# HTTPS 端口(SERVER_SSL_ENABLED=false 时为 HTTP)
EXPOSE 80

ENTRYPOINT ["./entrypoint.sh"]
