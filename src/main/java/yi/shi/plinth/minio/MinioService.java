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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final HttpClient URL_FETCHER = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final Pattern CD_FILENAME_STAR = Pattern.compile("filename\\*\\s*=\\s*([^;]+)");
    private static final Pattern CD_FILENAME = Pattern.compile("filename\\s*=\\s*\"?([^\";]+)\"?");

    /**
     * URL 拉取进度回调，在 MinIO SDK 读取源数据流的线程中同步调用。
     * 回调中写 HTTP 响应时抛 IOException 会中断后续拉取（如客户端已断开）。
     */
    @FunctionalInterface
    public interface FetchProgress {
        /**
         * @param filename   已确定的文件名（{@code download/} 之后的部分）
         * @param readBytes  已从源读取并转发给 MinIO 的字节数
         * @param totalBytes 源 Content-Length；-1 表示未知
         */
        void accept(String filename, long readBytes, long totalBytes) throws Exception;
    }

    /** 无进度回调的便捷重载。 */
    public String fetchUrlToBucket(String bucket, String sourceUrl) throws Exception {
        return fetchUrlToBucket(bucket, sourceUrl, (name, read, total) -> { });
    }

    /**
     * 从 HTTP(S) URL 服务端拉取文件并存入桶内 {@code download/} 目录（目录不存在则自动创建占位对象）。
     * 流式转发（不在应用内存中缓冲整个文件），未知 Content-Length 时按 10MB 分片上传。
     *
     * @param progress 字节进度回调（首事件为 0/total，末事件为 100%）
     * @return 实际存储的对象 key，如 {@code download/report.pdf}（重名自动加序号）
     */
    public String fetchUrlToBucket(String bucket, String sourceUrl, FetchProgress progress) throws Exception {
        URI uri;
        try {
            uri = URI.create(sourceUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid URL");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("invalid URL: missing host");
        }
        ensureBucket(bucket);
        // 文件夹占位对象（与 New Folder 一致），已存在则跳过
        if (statObject(bucket, "download/") == null) {
            makeFolder(bucket, "download");
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "minio-shell-url-fetch")
                .GET().build();
        HttpResponse<InputStream> resp = URL_FETCHER.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            try (InputStream ignored = resp.body()) {
                throw new IllegalStateException("source returned HTTP " + resp.statusCode());
            }
        }
        long length = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
        String contentType = resp.headers().firstValue("Content-Type").orElse(null);
        // 重定向后 resp.uri() 是最终地址，文件名应取自最终响应
        String filename = resolveDownloadName(resp.uri(), resp.headers().firstValue("Content-Disposition").orElse(null));
        String object = uniqueObjectName(bucket, "download/" + filename);

        progress.accept(filename, 0L, length);
        try (CountingInputStream in = new CountingInputStream(resp.body(), progress, filename, length)) {
            PutObjectArgs.Builder b = PutObjectArgs.builder()
                    .bucket(bucket).object(object).contentType(contentType);
            if (length >= 0) {
                b.stream(in, length, -1L);
            } else {
                b.stream(in, -1L, 10L * 1024 * 1024); // 长度未知：10MB 分片
            }
            client.putObject(b.build());
            progress.accept(filename, in.count, length); // 收尾事件（未知长度时给出最终字节数）
        }
        log.info("Fetched {} -> {}/{} ({} bytes)", sourceUrl, bucket, object, length);
        return object;
    }

    /**
     * 计数输入流：MinIO SDK 每读一块就回调一次进度（按 1% 或 500ms 节流，未知长度时按 256KB）。
     * 回调抛出的受检异常包装为 IOException，传播后中断 putObject（如浏览器侧已断开）。
     */
    private static final class CountingInputStream extends FilterInputStream {
        private final FetchProgress progress;
        private final String filename;
        private final long total;
        private long count;
        private long lastSent;
        private long lastEmitMs = System.currentTimeMillis();

        private CountingInputStream(InputStream in, FetchProgress progress, String filename, long total) {
            super(in);
            this.progress = progress;
            this.filename = filename;
            this.total = total;
        }

        @Override
        public int read() throws IOException {
            int n = in.read();
            if (n >= 0) {
                count++;
                maybeEmit();
            }
            return n;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            if (n > 0) {
                count += n;
                maybeEmit();
            }
            return n;
        }

        private void maybeEmit() throws IOException {
            long now = System.currentTimeMillis();
            long step = total > 0 ? Math.max(65536, total / 100) : 262144;
            if (count - lastSent < step && now - lastEmitMs < 500) {
                return;
            }
            lastSent = count;
            lastEmitMs = now;
            try {
                progress.accept(filename, count, total);
            } catch (IOException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    /** 重名时在扩展名前加 (1)/(2)...，避免静默覆盖已有对象。 */
    private String uniqueObjectName(String bucket, String object) {
        if (statObject(bucket, object) == null) {
            return object;
        }
        int dot = object.lastIndexOf('.');
        int slash = object.lastIndexOf('/');
        String base = dot > slash ? object.substring(0, dot) : object;
        String ext = dot > slash ? object.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            String candidate = base + " (" + i + ")" + ext;
            if (statObject(bucket, candidate) == null) {
                return candidate;
            }
        }
        return base + " (" + System.currentTimeMillis() + ")" + ext;
    }

    /** 文件名优先级：Content-Disposition（RFC 5987 filename* 优先）> 最终 URL 路径末段 > 兜底名；并做清洗/截断。 */
    private static String resolveDownloadName(URI uri, String contentDisposition) {
        String name = "";
        if (contentDisposition != null) {
            Matcher star = CD_FILENAME_STAR.matcher(contentDisposition);
            if (star.find()) {
                String v = star.group(1).trim();
                int q = v.indexOf('\'');
                int q2 = q >= 0 ? v.indexOf('\'', q + 1) : -1;
                name = q2 >= 0 ? v.substring(q2 + 1) : v;
                name = URLDecoder.decode(name, StandardCharsets.UTF_8);
            } else {
                Matcher plain = CD_FILENAME.matcher(contentDisposition);
                if (plain.find()) {
                    name = plain.group(1).trim();
                }
            }
        }
        if (name.isBlank()) {
            String path = uri.getPath();
            if (path != null) {
                int slash = path.lastIndexOf('/');
                if (slash >= 0 && slash < path.length() - 1) {
                    name = URLDecoder.decode(path.substring(slash + 1), StandardCharsets.UTF_8);
                }
            }
        }
        // 去掉路径分隔符、Windows 非法字符及控制字符
        name = name.replaceAll("[/\\:*?\"<>|\\x00-\\x1f]", "_").trim();
        name = name.replaceAll("^\\.+", "").replaceAll("\\.+$", "");
        if (name.isBlank()) {
            name = "file-" + System.currentTimeMillis();
        }
        if (name.length() > 180) {
            int dot = name.lastIndexOf('.');
            name = dot > 150 ? name.substring(0, 180 - (name.length() - dot)) + name.substring(dot)
                             : name.substring(0, 180);
        }
        return name;
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
