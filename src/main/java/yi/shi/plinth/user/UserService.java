package yi.shi.plinth.user;

import cn.dev33.satoken.stp.StpUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import yi.shi.plinth.db.entity.User;
import yi.shi.plinth.db.mapper.UserMapper;
import yi.shi.plinth.auth.AuthHelper;
import yi.shi.plinth.minio.MinioService;
import yi.shi.plinth.utils.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 用户业务服务：注册 / 登录 / 登出 / 当前用户 / 列表 / 详情 / 改角色 / 改密码 / 删除。
 *
 * <p>角色以逗号分隔字符串存库，登录时拆分为数组交给 {@link AuthHelper} 写入 Redis。
 * 对外返回的 {@link User} 一律脱敏（清空 password / salt）。
 */
@Singleton
public class UserService {

    private static final String DEFAULT_ROLES = "user";

    @Inject
    private UserMapper userMapper;

    @Inject
    private MinioService minioService;

    /** 注册新用户，返回脱敏后的用户。roles 为空时默认 "user"。 */
    public User register(String username, String password, String roles) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
        if (userMapper.findByUsername(username) != null) {
            throw new IllegalArgumentException("username already exists");
        }
        String salt = PasswordEncoder.generateSalt();
        User user = new User();
        user.setUsername(username);
        user.setPassword(PasswordEncoder.hash(password, salt));
        user.setSalt(salt);
        user.setRoles((roles == null || roles.isBlank()) ? DEFAULT_ROLES : roles);
        user.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insert(user);
        ensureCredentials(user);
        return sanitize(user);
    }

    /** 登录：校验账号密码与状态，成功则写入 sa-token 会话与角色，返回 token。 */
    public String login(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null || !PasswordEncoder.verify(password, user.getSalt(), user.getPassword())) {
            throw new IllegalArgumentException("invalid username or password");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new IllegalStateException("account disabled");
        }
        AuthHelper.login(user.getId(), splitRoles(user.getRoles()));
        return StpUtil.getTokenValue();
    }

    /** 登出当前会话。 */
    public void logout() {
        AuthHelper.logout();
    }

    /** 获取当前登录用户（脱敏）。惰性补全 MinIO 凭据（兼容老用户）。 */
    public User currentUser() {
        User user = userMapper.findById(StpUtil.getLoginIdAsLong());
        if (user != null) {
            ensureCredentials(user);
        }
        return sanitize(user);
    }

    /** 分页查询用户列表（脱敏，且不回传 S3 access/secret）。 */
    public PageResult<User> list(int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 10;
        }
        int offset = (page - 1) * size;
        List<User> users = userMapper.findPage(offset, size);
        users.forEach(u -> {
            sanitize(u);
            u.setMinioAccessKey(null);
            u.setMinioSecretKey(null);
        });
        return new PageResult<>(users, userMapper.count(), page, size);
    }

    /** 按 id 查询用户（脱敏）。惰性补全 MinIO 凭据（兼容老用户）。 */
    public User get(Long id) {
        User user = userMapper.findById(id);
        if (user != null) {
            ensureCredentials(user);
        }
        return sanitize(user);
    }

    /** 按 S3 access key 查询用户（不脱敏：/s3/* 代理验签需要 secret）。 */
    public User findByAccessKey(String accessKey) {
        if (accessKey == null || accessKey.isBlank()) {
            return null;
        }
        return userMapper.findByAccessKey(accessKey);
    }

    /** 重新生成该用户的 S3 access key / secret（旧密钥立即失效）。 */
    public User regenerateAccessKey(Long id) {
        User user = requireUser(id);
        user.setMinioAccessKey(minioService.generateAccessKey());
        user.setMinioSecretKey(minioService.generateSecretKey());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateMinioCredentials(user);
        return sanitize(user);
    }

    /** 修改用户角色。 */
    public User updateRoles(Long id, String roles) {
        User user = requireUser(id);
        user.setRoles(roles);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.update(user);
        return sanitize(user);
    }

    /** 修改密码：校验旧密码后重置 salt 与 hash。 */
    public void changePassword(Long id, String oldPassword, String newPassword) {
        User user = requireUser(id);
        if (!PasswordEncoder.verify(oldPassword, user.getSalt(), user.getPassword())) {
            throw new IllegalArgumentException("old password incorrect");
        }
        String salt = PasswordEncoder.generateSalt();
        user.setSalt(salt);
        user.setPassword(PasswordEncoder.hash(newPassword, salt));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.update(user);
    }

    /** 管理员重置用户密码为默认值 123456。 */
    public void resetPassword(Long id) {
        User user = requireUser(id);
        String salt = PasswordEncoder.generateSalt();
        user.setSalt(salt);
        user.setPassword(PasswordEncoder.hash("123456", salt));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.update(user);
    }

    /** 删除用户。 */
    public void delete(Long id) {
        userMapper.deleteById(id);
    }

    private User requireUser(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        return user;
    }

    /**
     * 确保用户已分配 MinIO 桶与 S3 access key/secret：缺失则生成并落库。
     * 桶创建（{@link MinioService#ensureBucket}）在 MinIO 不可达时仅记录警告，不抛异常，
     * 后续文件操作前会再次幂等尝试。
     */
    private void ensureCredentials(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        boolean changed = false;
        if (user.getBucket() == null || user.getBucket().isBlank()) {
            user.setBucket(minioService.userBucketName(user.getId()));
            minioService.ensureBucket(user.getBucket());
            changed = true;
        }
        if (user.getMinioAccessKey() == null || user.getMinioAccessKey().isBlank()) {
            user.setMinioAccessKey(minioService.generateAccessKey());
            user.setMinioSecretKey(minioService.generateSecretKey());
            changed = true;
        }
        if (changed) {
            user.setUpdateTime(LocalDateTime.now());
            userMapper.updateMinioCredentials(user);
        }
    }

    private User sanitize(User user) {
        if (user != null) {
            user.setPassword(null);
            user.setSalt(null);
        }
        return user;
    }

    private String[] splitRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
}
