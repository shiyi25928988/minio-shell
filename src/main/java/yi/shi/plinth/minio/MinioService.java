package yi.shi.plinth.minio;

import com.google.inject.Singleton;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import yi.shi.plinth.file.dto.FileItem;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * MinIO 数据面服务（{@code @Singleton}）：用管理员凭据操作所有用户桶。
 *
 * <p>构造期从系统属性 {@code minio.endpoint / minio.accessKey / minio.secretKey / minio.region}
 * 构建 {@link MinioClient}。{@link MinioClient#builder()} 不会立即连接，故即使 MinIO 暂不可用也能完成构造；
 * 真正的连接发生在首次操作（如 {@link #ensureBucket}）时。
 *
 * <p>用户桶名 = {@code minio.bucketPrefix} + 用户 id（默认 {@code user-<id>}）。
 * 每用户独立桶，隔离在 {@link yi.shi.plinth.file.FileApi} 与 {@link yi.shi.plinth.proxy.MinioProxyServlet}
 * 两处按当前用户固定桶名。
 */
@Slf4j
@Singleton
public class MinioService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_DATE_TIME;
    private static final String ACCESS_KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String SECRET_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789/+";

    private final MinioClient client;
    private final String bucketPrefix;

    public MinioService() {
        String endpoint = System.getProperty("minio.endpoint", "http://127.0.0.1:9000");
        String accessKey = System.getProperty("minio.accessKey", "minioadmin");
        String secretKey = System.getProperty("minio.secretKey", "minioadmin");
        String region = System.getProperty("minio.region", "");
        MinioClient.Builder b = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey);
        if (region != null && !region.isBlank()) {
            b.region(region);
        }
        this.client = b.build();
        this.bucketPrefix = System.getProperty("minio.bucketPrefix", "user-");
        log.info("MinioService initialized: endpoint={}, bucketPrefix={}", endpoint, bucketPrefix);
    }

    /** 用户桶名：前缀 + id，如 user-1。 */
    public String userBucketName(Long userId) {
        return bucketPrefix + userId;
    }

    /** 确保桶存在（不存在则创建）。MinIO 不可达时仅记录警告，不抛出。 */
    public void ensureBucket(String bucket) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket created: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("ensureBucket({}) failed: {}", bucket, e.getMessage());
        }
    }

    /** 列出桶内指定前缀下的对象/子前缀（非递归，适合文件浏览器逐层展开）。 */
    public List<FileItem> listObjects(String bucket, String prefix, boolean recursive) {
        List<FileItem> items = new ArrayList<>();
        String p = normalizePrefix(prefix);
        Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(p)
                .recursive(recursive)
                .build());
        for (Result<Item> r : results) {
            try {
                Item item = r.get();
                // 跳过当前文件夹自身的占位对象：进入 "myfolder/" 后，占位对象 "myfolder/" 本身
                // 也会被列出，它的 display 计算为空，会显示成"没有名字的文件"，应排除。
                if (item.objectName().equals(p)) {
                    continue;
                }
                FileItem fi = new FileItem();
                fi.setName(item.objectName());
                fi.setDir(item.isDir());
                fi.setDisplay(displayName(item.objectName(), item.isDir()));
                if (!item.isDir()) {
                    fi.setSize(item.size());
                    if (item.lastModified() != null) {
                        fi.setLastModified(item.lastModified().format(ISO));
                    }
                }
                items.add(fi);
            } catch (Exception e) {
                log.warn("listObjects({}/{}) item error: {}", bucket, p, e.getMessage());
            }
        }
        return items;
    }

    /** 上传对象：流式（已知 size，partSize=-1 让 SDK 自动选择单次/分片）。 */
    public void uploadObject(String bucket, String object, InputStream stream, long size, String contentType) throws Exception {
        PutObjectArgs.Builder b = PutObjectArgs.builder()
                .bucket(bucket)
                .object(object)
                .stream(stream, size, -1);
        if (contentType != null && !contentType.isBlank()) {
            b.contentType(contentType);
        }
        client.putObject(b.build());
    }

    /** 下载对象，返回流（调用方负责关闭）。 */
    public GetObjectResponse getObject(String bucket, String object) throws Exception {
        return client.getObject(GetObjectArgs.builder().bucket(bucket).object(object).build());
    }

    /** 对象元信息（size / contentType）。不存在时返回 null。 */
    public StatObjectResponse statObject(String bucket, String object) {
        try {
            return client.statObject(StatObjectArgs.builder().bucket(bucket).object(object).build());
        } catch (ErrorResponseException e) {
            return null;
        } catch (Exception e) {
            log.warn("statObject({}/{}) error: {}", bucket, object, e.getMessage());
            return null;
        }
    }

    /** 删除对象。 */
    public void deleteObject(String bucket, String object) throws Exception {
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(object).build());
    }

    /** 列出所有桶（admin 用，查看全部用户）。 */
    public List<Bucket> listBuckets() throws Exception {
        return client.listBuckets();
    }

    /** 生成 20 位随机 access key（字母数字）。 */
    public String generateAccessKey() {
        return randomString(20, ACCESS_KEY_CHARS);
    }

    /** 生成 40 位随机 secret key。 */
    public String generateSecretKey() {
        return randomString(40, SECRET_CHARS);
    }

    /** 创建"文件夹"：写入一个以 '/' 结尾的占位对象（0 字节）。 */
    public void makeFolder(String bucket, String folderPath) throws Exception {
        String normalized = normalizeFolderPath(folderPath);
        client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(normalized)
                .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                .contentType("application/x-directory")
                .build());
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank() || prefix.equals("/")) {
            return "";
        }
        String p = prefix.startsWith("/") ? prefix.substring(1) : prefix;
        return p.endsWith("/") ? p : p + "/";
    }

    private static String normalizeFolderPath(String folderPath) {
        String p = folderPath == null ? "" : folderPath.trim();
        if (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (!p.isEmpty() && !p.endsWith("/")) {
            p = p + "/";
        }
        return p;
    }

    private static String displayName(String objectName, boolean isDir) {
        if (objectName == null) {
            return "";
        }
        String name = objectName;
        // 以 '/' 结尾的是文件夹占位/前缀，去掉末尾斜杠再取最后一段，避免显示空名
        if (name.endsWith("/")) {
            name = name.substring(0, name.length() - 1);
        }
        int idx = name.lastIndexOf('/');
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    private static String randomString(int length, String alphabet) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
