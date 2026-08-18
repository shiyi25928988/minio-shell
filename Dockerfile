# 运行阶段:JDK 21 JRE
# 所有可配置项通过环境变量注入(见 docker-compose.yml 的 environment 段;
# entrypoint.sh 内置同款默认值,裸 docker run 时不传也能启动)
FROM eclipse-temurin:21-jre

WORKDIR /app

# 复制 fat jar 与启动脚本(需先在宿主执行 mvn package)
COPY target/plinth-jre-21.jar app.jar
COPY entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

# 持久化:H2 数据 + 证书。
# VOLUME 仅为匿名卷声明;要让数据库文件落在宿主机,请用绑定挂载:
#   docker run -v "$PWD/data:/app/data" ...
# 或直接使用 docker-compose.yml(./data:/app/data)。
RUN mkdir -p /app/data /app/certs
VOLUME ["/app/data", "/app/certs"]

# HTTP 端口(SERVER_SSL_ENABLED=true 时为 HTTPS)
EXPOSE 80

ENTRYPOINT ["./entrypoint.sh"]
