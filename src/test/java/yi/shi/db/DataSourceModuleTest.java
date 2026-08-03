package yi.shi.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import yi.shi.db.entity.Account;
import yi.shi.db.mapper.AccountMapper;
import yi.shi.plinth.modules.DataSourceModule;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证 {@link DataSourceModule}：扫描 {@code yi.shi.db.mapper} 下的 {@code @Mapper} 接口、
 * 通过 {@code JDBC.*} 属性建好池化数据源，并完成一次端到端查询。
 *
 * 使用 H2 内存库（MySQL 兼容模式），不依赖外部数据库，{@code mvn test} 可直接运行。
 */
public class DataSourceModuleTest {

    private static final String H2_URL = "jdbc:h2:mem:plinthtest;MODE=MySQL;DB_CLOSE_DELAY=-1";

    private static Injector injector;

    @BeforeAll
    static void setUp() throws Exception {
        // 用 H2 覆盖 JDBC.* 属性（DataSourceModule 经 PooledDataSourceProvider 读取）
        System.setProperty("JDBC.driver", "org.h2.Driver");
        System.setProperty("JDBC.url", H2_URL);
        System.setProperty("JDBC.username", "sa");
        System.setProperty("JDBC.password", "");
        // mybatis-guice 的 EnvironmentProvider 需要 mybatis.environment.id（真实使用中由 application.properties 提供）
        System.setProperty("mybatis.environment.id", "development");

        // 建表并预置一条数据
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE account (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            st.execute("INSERT INTO account VALUES (1, 'shiyi')");
        }

        injector = Guice.createInjector(new DataSourceModule("yi.shi.db.mapper"));
    }

    @AfterAll
    static void tearDown() {
        System.clearProperty("JDBC.driver");
        System.clearProperty("JDBC.url");
        System.clearProperty("JDBC.username");
        System.clearProperty("JDBC.password");
        System.clearProperty("mybatis.environment.id");
    }

    @Test
    void mapperScannedAndQueryWorks() {
        SqlSessionFactory factory = injector.getInstance(SqlSessionFactory.class);
        try (SqlSession session = factory.openSession()) {
            AccountMapper mapper = session.getMapper(AccountMapper.class);

            // 预置数据可查到，验证 mapper 已被扫描注册且数据源连通
            Account account = mapper.getById(1L);
            assertNotNull(account);
            assertEquals("shiyi", account.getName());

            // 通过 mapper 写入并回读，验证写路径
            assertEquals(1, mapper.insert(2L, "plinth"));
            Account inserted = mapper.getById(2L);
            assertNotNull(inserted);
            assertEquals("plinth", inserted.getName());
        }
    }
}
