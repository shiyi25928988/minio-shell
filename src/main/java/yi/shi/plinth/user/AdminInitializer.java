package yi.shi.plinth.user;

import com.google.inject.Injector;
import lombok.extern.slf4j.Slf4j;
import yi.shi.plinth.db.entity.User;
import yi.shi.plinth.db.mapper.UserMapper;
import yi.shi.plinth.utils.PasswordEncoder;

import java.time.LocalDateTime;

/**
 * 启动时确保存在默认管理员账号。
 *
 * <p>仅当应用注册了 {@code DataSourceModule}（即 UserMapper 可注入）时生效。
 * 若配置的默认用户名不存在，则用 {@link PasswordEncoder} 哈希密码后创建，
 * 角色为 {@code admin,user}。用户名/密码由 {@code admin.default.username} /
 * {@code admin.default.password} 配置，默认均为 {@code admin}。
 */
@Slf4j
public final class AdminInitializer {

    private AdminInitializer() {
    }

    public static void ensureDefaultAdmin(Injector injector) {
        UserMapper userMapper;
        try {
            userMapper = injector.getInstance(UserMapper.class);
        } catch (Exception e) {
            // 未注册 DataSourceModule，无 UserMapper 绑定，跳过
            return;
        }
        try {
            String username = System.getProperty("admin.default.username", "admin");
            String password = System.getProperty("admin.default.password", "admin");
            if (userMapper.findByUsername(username) != null) {
                return;
            }
            String salt = PasswordEncoder.generateSalt();
            User user = new User();
            user.setUsername(username);
            user.setPassword(PasswordEncoder.hash(password, salt));
            user.setSalt(salt);
            user.setRoles("admin,user");
            user.setStatus(1);
            LocalDateTime now = LocalDateTime.now();
            user.setCreateTime(now);
            user.setUpdateTime(now);
            userMapper.insert(user);
            log.info("Default admin [{}] created", username);
        } catch (Exception e) {
            log.warn("Skip default admin init: {}", e.getMessage());
        }
    }
}
