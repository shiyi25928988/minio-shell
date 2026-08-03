package yi.shi.plinth.db;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 启动时自动执行建表 SQL（sql/sys_user.sql），CREATE TABLE IF NOT EXISTS 幂等。
 *
 * <p>用独立的 JDBC 连接执行（不走 MyBatis），在 AdminInitializer 之前运行。
 * 需配置 JDBC.driver / JDBC.url / JDBC.username / JDBC.password；未配置则跳过。
 */
@Slf4j
public final class SchemaInitializer {

    private static final List<String> SCHEMA_SQL_FILES = Arrays.asList(
            "/sql/sys_user.sql", "/sql/sys_share.sql");

    private SchemaInitializer() {
    }

    public static void ensureSchema() {
        String url = System.getProperty("JDBC.url");
        if (url == null || url.isEmpty()) {
            return;
        }
        String driver = System.getProperty("JDBC.driver");
        String user = System.getProperty("JDBC.username");
        String password = System.getProperty("JDBC.password", "");
        try {
            if (driver != null && !driver.isEmpty()) {
                Class.forName(driver);
            }
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 Statement stmt = conn.createStatement()) {
                for (String sqlFile : SCHEMA_SQL_FILES) {
                    String sql = readResource(sqlFile);
                    if (sql == null) {
                        log.warn("Schema script not found on classpath: {}", sqlFile);
                        continue;
                    }
                    for (String segment : sql.split(";")) {
                        String cleaned = stripComments(segment);
                        if (!cleaned.isEmpty()) {
                            stmt.execute(cleaned);
                        }
                    }
                }
            }
            log.info("Schema initialized (sys_user, sys_share)");
        } catch (Exception e) {
            log.warn("Skip schema init: {}", e.getMessage());
        }
    }

    /** 去掉 -- 注释行，保留 SQL 主体。 */
    private static String stripComments(String segment) {
        return Arrays.stream(segment.split("\n"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"))
                .trim();
    }

    private static String readResource(String path) {
        try (InputStream in = SchemaInitializer.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
