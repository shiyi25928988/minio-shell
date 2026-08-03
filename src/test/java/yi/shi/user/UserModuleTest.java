package yi.shi.user;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import yi.shi.plinth.db.entity.User;
import yi.shi.plinth.db.mapper.UserMapper;
import yi.shi.plinth.modules.DataSourceModule;
import yi.shi.plinth.user.PageResult;
import yi.shi.plinth.user.UserService;
import yi.shi.plinth.utils.PasswordEncoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户模块测试：PasswordEncoder、UserMapper、UserService 的注册/查询/列表/改角色/改密码/删除。
 * 使用 H2 内存库（MySQL 兼容模式），login 因依赖 sa-token 请求上下文不在此单测。
 */
public class UserModuleTest {

    private static final String H2_URL = "jdbc:h2:mem:usermodtest;MODE=MySQL;DB_CLOSE_DELAY=-1";

    private static Injector injector;
    private static UserService userService;
    private static UserMapper userMapper;

    @BeforeAll
    static void setUp() throws Exception {
        System.setProperty("JDBC.driver", "org.h2.Driver");
        System.setProperty("JDBC.url", H2_URL);
        System.setProperty("JDBC.username", "sa");
        System.setProperty("JDBC.password", "");
        System.setProperty("mybatis.environment.id", "development");

        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE sys_user ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "username VARCHAR(64) NOT NULL UNIQUE, "
                    + "password VARCHAR(128) NOT NULL, "
                    + "salt VARCHAR(64) NOT NULL, "
                    + "roles VARCHAR(128), "
                    + "status INT DEFAULT 1, "
                    + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP, "
                    + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP, "
                    + "bucket VARCHAR(128), "
                    + "minio_access_key VARCHAR(64), "
                    + "minio_secret_key VARCHAR(128))");
        }

        injector = Guice.createInjector(new DataSourceModule());
        userService = injector.getInstance(UserService.class);
        userMapper = injector.getInstance(UserMapper.class);
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
    void passwordHashAndVerify() {
        String salt = PasswordEncoder.generateSalt();
        String hash = PasswordEncoder.hash("secret123", salt);
        assertTrue(PasswordEncoder.verify("secret123", salt, hash));
        assertFalse(PasswordEncoder.verify("wrong", salt, hash));
        assertFalse(PasswordEncoder.verify("secret123", salt, null));
        // 不同 salt 产生不同 hash
        assertNotEquals(hash, PasswordEncoder.hash("secret123", PasswordEncoder.generateSalt()));
    }

    @Test
    void registerAndQuery() {
        User user = userService.register("alice", "pwdAlice", "admin,user");
        assertNotNull(user.getId());
        assertNull(user.getPassword(), "脱敏后不应返回 password");
        assertNull(user.getSalt(), "脱敏后不应返回 salt");
        assertEquals("admin,user", user.getRoles());

        User found = userService.get(user.getId());
        assertEquals("alice", found.getUsername());
        assertNull(found.getPassword());
    }

    @Test
    void registerDefaultRolesWhenBlank() {
        User user = userService.register("alice_default", "pwd", null);
        assertEquals("user", user.getRoles());
    }

    @Test
    void registerDuplicateFails() {
        userService.register("bob", "pwdBob", null);
        assertThrows(IllegalArgumentException.class,
                () -> userService.register("bob", "x", null));
    }

    @Test
    void listAndUpdateRoles() {
        userService.register("carol", "pwdCarol", "user");
        PageResult<User> page = userService.list(1, 10);
        assertTrue(page.getTotal() >= 1);

        User carol = userMapper.findByUsername("carol");
        userService.updateRoles(carol.getId(), "admin");
        assertEquals("admin", userService.get(carol.getId()).getRoles());
    }

    @Test
    void changePassword() {
        userService.register("dave", "pwdDave", "user");
        User dave = userMapper.findByUsername("dave");
        userService.changePassword(dave.getId(), "pwdDave", "newPwd");

        User raw = userMapper.findByUsername("dave");
        assertTrue(PasswordEncoder.verify("newPwd", raw.getSalt(), raw.getPassword()));
        assertFalse(PasswordEncoder.verify("pwdDave", raw.getSalt(), raw.getPassword()));
    }

    @Test
    void changePasswordWithWrongOldFails() {
        userService.register("dave2", "pwdDave", "user");
        User dave = userMapper.findByUsername("dave2");
        assertThrows(IllegalArgumentException.class,
                () -> userService.changePassword(dave.getId(), "wrongOld", "newPwd"));
    }

    @Test
    void delete() {
        userService.register("eve", "pwdEve", "user");
        User eve = userMapper.findByUsername("eve");
        userService.delete(eve.getId());
        assertNull(userMapper.findByUsername("eve"));
    }
}
