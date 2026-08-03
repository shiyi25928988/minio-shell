package yi.shi.plinth.share;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.minio.StatObjectResponse;
import lombok.extern.slf4j.Slf4j;
import yi.shi.plinth.db.entity.Share;
import yi.shi.plinth.db.mapper.ShareMapper;
import yi.shi.plinth.minio.MinioService;
import yi.shi.plinth.share.dto.ShareInfo;
import yi.shi.plinth.utils.PasswordEncoder;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件分享业务：创建 / 列表 / 撤销 / 校验 / 消费下载。
 *
 * <p>token 为 32 位随机串；密码用 {@link PasswordEncoder}（SHA-256+salt）哈希；
 * 下载计数用 {@link ShareMapper#incrementDownloadCount} 原子自增（含过期/次数检查），防并发超限。
 */
@Slf4j
@Singleton
public class ShareService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TOKEN_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TOKEN_LENGTH = 32;

    @Inject
    private ShareMapper shareMapper;

    @Inject
    private MinioService minioService;

    /** 创建分享：校验对象存在后生成 token 落库，返回分享信息。 */
    public ShareInfo create(String bucket, String path, Long creatorId,
                            Integer expireDays, String password, Integer maxCount) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        StatObjectResponse stat = minioService.statObject(bucket, path);
        if (stat == null) {
            throw new IllegalArgumentException("file not found: " + path);
        }
        Share share = new Share();
        share.setToken(generateToken());
        share.setBucket(bucket);
        share.setObjectName(path);
        share.setFilename(displayName(path));
        share.setSize(stat.size());
        share.setCreatorId(creatorId);
        share.setExpireTime((expireDays != null && expireDays > 0)
                ? LocalDateTime.now().plusDays(expireDays) : null);
        if (password != null && !password.isBlank()) {
            String salt = PasswordEncoder.generateSalt();
            share.setPasswordSalt(salt);
            share.setPasswordHash(PasswordEncoder.hash(password, salt));
        }
        share.setMaxCount((maxCount != null && maxCount > 0) ? maxCount : null);
        share.setDownloadCount(0);
        share.setCreateTime(LocalDateTime.now());
        shareMapper.insert(share);
        return toInfo(share);
    }

    public List<ShareInfo> listByCreator(Long creatorId) {
        return shareMapper.listByCreator(creatorId).stream()
                .map(this::toInfo)
                .collect(Collectors.toList());
    }

    /** 撤销：仅创建者可撤自己的。返回是否成功。 */
    public boolean revoke(Long creatorId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return shareMapper.deleteByTokenAndCreator(token, creatorId) > 0;
    }

    public Share getByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return shareMapper.findByToken(token);
    }

    /**
     * 校验访问权限（不计数）：用于 /share/check 预校验密码。
     * 校验 token 存在、未过期、密码正确（若有）。失败抛异常。
     */
    public Share checkAccess(String token, String password) {
        Share share = requireValid(token);
        verifyPassword(share, password);
        return share;
    }

    /**
     * 消费一次下载：校验 + 原子计数。
     * 校验 token 存在、未过期、密码正确，然后 incrementDownloadCount（原子检查次数）。
     * 失败抛异常（share not found / expired / password incorrect / download limit reached）。
     */
    public Share consumeDownload(String token, String password) {
        Share share = requireValid(token);
        verifyPassword(share, password);
        int affected = shareMapper.incrementDownloadCount(token);
        if (affected == 0) {
            // 到这里说明已通过过期/密码校验，0 只可能是次数已达上限
            throw new IllegalStateException("download limit reached");
        }
        return share;
    }

    public ShareInfo toInfo(Share share) {
        if (share == null) {
            return null;
        }
        ShareInfo info = new ShareInfo();
        info.setToken(share.getToken());
        info.setUrl("/share/view?token=" + share.getToken());
        info.setFilename(share.getFilename());
        info.setSize(share.getSize() != null ? share.getSize() : 0L);
        info.setExpireTime(share.getExpireTime() != null ? share.getExpireTime().toString() : null);
        info.setHasPassword(share.getPasswordHash() != null);
        info.setMaxCount(share.getMaxCount());
        info.setDownloadCount(share.getDownloadCount() != null ? share.getDownloadCount() : 0);
        info.setCreateTime(share.getCreateTime() != null ? share.getCreateTime().toString() : null);
        return info;
    }

    private Share requireValid(String token) {
        Share share = getByToken(token);
        if (share == null) {
            throw new IllegalArgumentException("share not found");
        }
        if (share.getExpireTime() != null && share.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("share expired");
        }
        return share;
    }

    private void verifyPassword(Share share, String password) {
        if (share.getPasswordHash() == null) {
            return;
        }
        if (password == null || !PasswordEncoder.verify(password, share.getPasswordSalt(), share.getPasswordHash())) {
            throw new IllegalArgumentException("password incorrect");
        }
    }

    private static String displayName(String path) {
        String name = path == null ? "" : path;
        int idx = name.lastIndexOf('/');
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    private static String generateToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(TOKEN_CHARS.charAt(RANDOM.nextInt(TOKEN_CHARS.length())));
        }
        return sb.toString();
    }
}
