package yi.shi.plinth.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 密码哈希工具：SHA-256 + 随机 salt（基于 JDK，无第三方依赖）。
 *
 * <p>每个密码独立生成 16 字节 salt，哈希 = SHA-256(salt + password)。
 * 校验使用恒定时间比较以避免计时侧信道。适用于内网/演示场景；
 * 对强度要求更高的生产环境建议改用 BCrypt/PBKDF2。
 */
public final class PasswordEncoder {

    private static final int SALT_LENGTH = 16;
    private static final String ALGORITHM = "SHA-256";

    private PasswordEncoder() {
    }

    /** 生成 16 字节随机 salt，返回 hex 字符串。 */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /** 计算 SHA-256(salt + password) 的 hex。 */
    public static String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /** 校验密码，恒定时间比较避免计时攻击。 */
    public static boolean verify(String password, String salt, String expectedHash) {
        if (expectedHash == null || salt == null || password == null) {
            return false;
        }
        return constantTimeEquals(hash(password, salt), expectedHash);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
