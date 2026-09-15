package yi.shi.plinth.minio;

import com.google.inject.Singleton;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SourceObject;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.admin.GetServerInfoResponse;
import io.minio.admin.MinioAdminClient;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import io.minio.messages.ListAllMyBucketsResult;
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
    private final MinioAdminClient adminClient;
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
        // Admin 客户端复用同一 endpoint/凭据，用于 getServerInfo 查询磁盘用量。
        MinioAdminClient.Builder ab = MinioAdminClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey);
        if (region != null && !region.isBlank()) {
            ab.region(region);
        }
        this.adminClient = ab.build();
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
                .stream(stream, size, -1L);
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

    /**
     * 重命名（S3 对象不可变，没有原生 rename）：服务端 copyObject 到新 key 后删除旧 key，
     * 数据只在 MinIO 内部拷贝，不经过应用。文件夹（以 '/' 结尾）则递归拷贝其前缀下全部对象。
     *
     * @param newName 新的单级名称（不含路径分隔符），由调用方校验
     * @return 新的对象路径（文件夹为以 '/' 结尾的新前缀）
     */
    public String renameObject(String bucket, String oldPath, String newName) throws Exception {
        boolean dir = oldPath.endsWith("/");
        String stripped = dir ? oldPath.substring(0, oldPath.length() - 1) : oldPath;
        int idx = stripped.lastIndexOf('/');
        String parent = idx >= 0 ? stripped.substring(0, idx + 1) : "";
        String oldPrefix = parent + stripped.substring(idx + 1) + (dir ? "/" : "");
        String newPrefix = parent + newName + (dir ? "/" : "");
        if (newPrefix.equals(oldPrefix)) {
            return oldPrefix; // 名字未变化，no-op（避免 copy 到自身后删除导致对象丢失）
        }
        if (dir) {
            // 目标前缀已被占用（有占位对象或子内容）则拒绝
            if (statObject(bucket, newPrefix) != null || !listObjects(bucket, newPrefix, false).isEmpty()) {
                throw new IllegalStateException("Target already exists: " + newName);
            }
            // 递归列出旧前缀下全部 key（含 0 字节占位对象本身）
            List<String> keys = new ArrayList<>();
            for (Result<Item> r : client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket).prefix(oldPrefix).recursive(true).build())) {
                keys.add(r.get().objectName());
            }
            for (String key : keys) {
                String suffix = key.substring(oldPrefix.length());
                copyInBucket(bucket, key, newPrefix + suffix);
            }
            for (String key : keys) {
                deleteObject(bucket, key);
            }
        } else {
            if (statObject(bucket, newPrefix) != null) {
                throw new IllegalStateException("Target already exists: " + newName);
            }
            copyInBucket(bucket, oldPath, newPrefix);
            deleteObject(bucket, oldPath);
        }
        return newPrefix;
    }

    /** 同桶内服务端拷贝，默认 COPY 元数据指令保留原 content-type。 */
    private void copyInBucket(String bucket, String source, String target) throws Exception {
        client.copyObject(CopyObjectArgs.builder()
                .bucket(bucket)
                .object(target)
                .source(SourceObject.builder().bucket(bucket).object(source).build())
                .build());
    }

    /** 列出所有桶（admin 用，查看全部用户）。 */
    public List<ListAllMyBucketsResult.Bucket> listBuckets() throws Exception {
        return client.listBuckets();
    }

    /**
     * 查询 MinIO 集群磁盘用量：聚合 {@code getServerInfo} 返回的每块磁盘的
     * {@code totalSpace/usedSpace/availableSpace}。
     *
     * <p>MinIO 不可达或管理员凭据无权限时返回 {@code null}（仅记录警告，不抛出），
     * 以免拖垮文件浏览器加载。
     */
    public DiskUsage getDiskUsage() {
        try {
            GetServerInfoResponse info = adminClient.getServerInfo();
            long total = 0, used = 0, avail = 0;
            int totalDisks = 0, onlineDisks = 0;
            if (info.servers() != null) {
                for (GetServerInfoResponse.ServerProperties sp : info.servers()) {
                    if (sp.disks() == null) {
                        continue;
                    }
                    for (GetServerInfoResponse.ServerProperties.Disk d : sp.disks()) {
                        totalDisks++;
                        String state = d.state();
                        if (state == null || !"offline".equalsIgnoreCase(state)) {
                            onlineDisks++;
                        }
                        if (d.totalSpace() != null) {
                            total += d.totalSpace().longValue();
                        }
                        if (d.usedSpace() != null) {
                            used += d.usedSpace().longValue();
                        }
                        if (d.availableSpace() != null) {
                            avail += d.availableSpace().longValue();
                        }
                    }
                }
            }
            double util = total > 0 ? (used * 100.0 / total) : 0.0;
            return new DiskUsage(total, used, avail, totalDisks, onlineDisks,
                    Math.round(util * 100.0) / 100.0);
        } catch (Exception e) {
            log.warn("getDiskUsage failed: {}", e.getMessage());
            return null;
        }
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
                .stream(new ByteArrayInputStream(new byte[0]), 0L, -1L)
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
