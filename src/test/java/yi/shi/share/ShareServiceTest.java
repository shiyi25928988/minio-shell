package yi.shi.share;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import yi.shi.plinth.db.entity.Share;
import yi.shi.plinth.db.mapper.ShareMapper;
import yi.shi.plinth.modules.DataSourceModule;
import yi.shi.plinth.share.ShareService;
import yi.shi.plinth.share.dto.ShareInfo;
import yi.shi.plinth.utils.PasswordEncoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ShareService 单测：校验/过期/密码/次数限制/撤销/列表逻辑（H2 内存库，不依赖 MinIO）。
 * create() 因需 MinIO statObject 不在此覆盖；check/consume/revoke 直接用预插记录测试。
 */
public class ShareServiceTest {

    private static final String H2_URL = "jdbc:h2:mem:sharetest;MODE=MySQL;DB_CLOSE_DELAY=-1";
    private static Injector injector;
    private static ShareService shareService;
    private static ShareMapper shareMapper;

    @BeforeAll
    static void setUp() throws Exception {
        System.setProperty("JDBC.driver", "org.h2.Driver");
        System.setProperty("JDBC.url", H2_URL);
        System.setProperty("JDBC.username", "sa");
        System.setProperty("JDBC.password", "");
        System.setProperty("mybatis.environment.id", "development");

        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE sys_share ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "token VARCHAR(64) NOT NULL UNIQUE, "
                    + "bucket VARCHAR(128) NOT NULL, "
                    + "object_name VARCHAR(1024) NOT NULL, "
                    + "filename VARCHAR(255), "
                    + "size BIGINT, "
                    + "creator_id BIGINT NOT NULL, "
                    + "expire_time DATETIME, "
                    + "password_hash VARCHAR(128), "
                    + "password_salt VARCHAR(64), "
                    + "max_count INT, "
                    + "download_count INT DEFAULT 0, "
                    + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP)");
        }

        injector = Guice.createInjector(new DataSourceModule());
        shareService = injector.getInstance(ShareService.class);
        shareMapper = injector.getInstance(ShareMapper.class);
    }

    @AfterAll
    static void tearDown() {
        System.clearProperty("JDBC.driver");
        System.clearProperty("JDBC.url");
        System.clearProperty("JDBC.username");
        System.clearProperty("JDBC.password");
        System.clearProperty("mybatis.environment.id");
    }

    private Share insertShare(String token, Long creatorId, Integer maxCount, LocalDateTime expire, String password) {
        Share s = new Share();
        s.setToken(token);
        s.setBucket("user-" + creatorId);
        s.setObjectName("user-" + creatorId + "/" + token + ".txt");
        s.setFilename(token + ".txt");
        s.setSize(100L);
        s.setCreatorId(creatorId);
        s.setExpireTime(expire);
        if (password != null && !password.isBlank()) {
            String salt = PasswordEncoder.generateSalt();
            s.setPasswordSalt(salt);
            s.setPasswordHash(PasswordEncoder.hash(password, salt));
        }
        s.setMaxCount(maxCount);
        s.setDownloadCount(0);
        s.setCreateTime(LocalDateTime.now());
        shareMapper.insert(s);
        return s;
    }

    @Test
    void checkNoPassword() {
        insertShare("tok-nopass", 1L, null, null, null);
        Share s = shareService.checkAccess("tok-nopass", null);
        assertNotNull(s);
        assertEquals("tok-nopass.txt", s.getFilename());
    }

    @Test
    void checkWithPassword() {
        insertShare("tok-pass", 1L, null, null, "secret");
        assertNotNull(shareService.checkAccess("tok-pass", "secret"));
        assertThrows(IllegalArgumentException.class, () -> shareService.checkAccess("tok-pass", "wrong"));
        assertThrows(IllegalArgumentException.class, () -> shareService.checkAccess("tok-pass", null));
    }

    @Test
    void downloadCountLimit() {
        insertShare("tok-limit", 1L, 2, null, null);
        assertNotNull(shareService.consumeDownload("tok-limit", null));
        assertNotNull(shareService.consumeDownload("tok-limit", null));
        // 第三次应超次数
        assertThrows(IllegalStateException.class, () -> shareService.consumeDownload("tok-limit", null));
        Share s = shareMapper.findByToken("tok-limit");
        assertEquals(2, s.getDownloadCount());
    }

    @Test
    void expired() {
        insertShare("tok-expired", 1L, null, LocalDateTime.now().minusHours(1), null);
        assertThrows(IllegalStateException.class, () -> shareService.checkAccess("tok-expired", null));
        assertThrows(IllegalStateException.class, () -> shareService.consumeDownload("tok-expired", null));
    }

    @Test
    void notFound() {
        assertThrows(IllegalArgumentException.class, () -> shareService.checkAccess("nonexistent", null));
    }

    @Test
    void revoke() {
        insertShare("tok-revoke", 1L, null, null, null);
        assertTrue(shareService.revoke(1L, "tok-revoke"));
        assertThrows(IllegalArgumentException.class, () -> shareService.checkAccess("tok-revoke", null));
    }

    @Test
    void revokeByOtherUserFails() {
        insertShare("tok-other", 1L, null, null, null);
        assertFalse(shareService.revoke(2L, "tok-other"));
        // 未删除，仍可访问
        assertNotNull(shareService.checkAccess("tok-other", null));
    }

    @Test
    void listByCreator() {
        insertShare("tok-list1", 5L, null, null, null);
        insertShare("tok-list2", 5L, null, null, null);
        insertShare("tok-list3", 6L, null, null, null);
        List<ShareInfo> list = shareService.listByCreator(5L);
        assertEquals(2, list.size());
    }
}
