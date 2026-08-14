package yi.shi.plinth.file;

import cn.dev33.satoken.stp.StpUtil;
import com.google.inject.Inject;
import jakarta.servlet.http.Part;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.HttpParam;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.annotation.http.Method.POST;
import yi.shi.plinth.db.entity.User;
import yi.shi.plinth.file.dto.FileItem;
import yi.shi.plinth.http.HttpRespHelper;
import yi.shi.plinth.http.MimeType;
import yi.shi.plinth.http.result.BINARY;
import yi.shi.plinth.http.result.JSON;
import yi.shi.plinth.minio.DiskUsage;
import yi.shi.plinth.minio.MinioService;
import yi.shi.plinth.servlet.ServletHelper;
import yi.shi.plinth.share.ShareService;
import yi.shi.plinth.user.UserService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件管理 HTTP 接口（Web UI 数据面）。
 *
 * <p>隔离规则：普通用户的 bucket 固定为其本人桶（{@link UserService#currentUser()} 惰性分配）；
 * admin 可通过 {@code bucket} 参数指定任意用户桶以查看全部文件。
 * 所有 MinIO 操作走 {@link MinioService}（管理员凭据）。
 */
@HttpService
public class FileApi {

    @Inject
    private MinioService minioService;

    @Inject
    private UserService userService;

    @Inject
    private ShareService shareService;

    /** 列出当前前缀下的对象/子文件夹（非递归）。 */
    @GET
    @HttpPath("/file/list")
    @AUTH
    public JSON<List<FileItem>> list(@HttpParam("prefix") String prefix,
                                     @HttpParam("bucket") String bucket) {
        String b = resolveBucket(bucket);
        minioService.ensureBucket(b);
        // 禁止缓存：上传/删除/建文件夹后 loadFiles 必须拿到最新列表
        ServletHelper.getResponse().setHeader("Cache-Control", "no-store");
        return new JSON<>(minioService.listObjects(b, prefix, false));
    }

    /** 递归搜索整个用户桶内名称匹配 q 的<b>文件</b>（不含文件夹）。 */
    @GET
    @HttpPath("/file/search")
    @AUTH
    public JSON<List<FileItem>> search(@HttpParam("q") String q,
                                       @HttpParam("bucket") String bucket) {
        String b = resolveBucket(bucket);
        minioService.ensureBucket(b);
        List<FileItem> all = minioService.listObjects(b, "", true);
        String ql = (q == null) ? "" : q.toLowerCase();
        boolean emptyQuery = ql.trim().isEmpty();
        List<FileItem> matched = new ArrayList<>();
        for (FileItem it : all) {
            // 只搜文件：跳过文件夹占位（以 '/' 结尾）
            if (it.getName() == null || it.getName().endsWith("/")) {
                continue;
            }
            if (emptyQuery
                    || it.getName().toLowerCase().contains(ql)
                    || (it.getDisplay() != null && it.getDisplay().toLowerCase().contains(ql))) {
                matched.add(it);
            }
        }
        return new JSON<>(matched);
    }

    /** 上传文件（multipart：表单字段 path/bucket + 一个或多个 file 部分）。 */
    @POST
    @HttpPath("/file/upload")
    @AUTH
    public JSON<List<Map<String, Object>>> upload(@HttpParam("path") String path,
                                                  @HttpParam("bucket") String bucket) throws Exception {
        String b = resolveBucket(bucket);
        minioService.ensureBucket(b);
        List<Map<String, Object>> uploaded = new ArrayList<>();
        for (Part part : ServletHelper.getRequest().getParts()) {
            String submitted = part.getSubmittedFileName();
            if (!"file".equals(part.getName()) || submitted == null || submitted.isBlank()) {
                continue;
            }
            // 防止路径穿越：仅取文件名本身
            String filename = submitted.substring(submitted.lastIndexOf('/') + 1);
            filename = filename.substring(filename.lastIndexOf('\\') + 1);
            String object = joinPath(path, filename);
            minioService.uploadObject(b, object, part.getInputStream(),
                    part.getSize(), part.getContentType());
            Map<String, Object> r = new HashMap<>();
            r.put("name", filename);
            r.put("path", object);
            r.put("size", part.getSize());
            uploaded.add(r);
        }
        if (uploaded.isEmpty()) {
            throw new IllegalArgumentException("file part is required");
        }
        return new JSON<>(uploaded);
    }

    /** 下载对象：流式回传，设真实 content-type 与 Content-Disposition。 */
    @GET
    @HttpPath("/file/download")
    @AUTH
    public BINARY download(@HttpParam("path") String path,
                           @HttpParam("bucket") String bucket) throws Exception {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        String b = resolveBucket(bucket);
        io.minio.StatObjectResponse stat = minioService.statObject(b, path);
        String contentType = (stat != null && stat.contentType() != null)
                ? stat.contentType() : MimeType.APPLICATION_OCTET_STREAM.getType();
        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        HttpRespHelper.setContentDisposition(filename);
        if (stat != null && stat.size() > 0) {
            ServletHelper.getResponse().setHeader("Content-Length", String.valueOf(stat.size()));
        }
        BINARY result = new BINARY();
        result.setData(minioService.getObject(b, path));
        result.setMimeType(MimeType.ALL);
        result.setRawContentType(contentType);
        return result;
    }

    /** 删除对象。文件夹（path 以 '/' 结尾）非空时拒绝删除。 */
    @GET
    @HttpPath("/file/delete")
    @AUTH
    public JSON<String> delete(@HttpParam("path") String path,
                               @HttpParam("bucket") String bucket) throws Exception {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        String b = resolveBucket(bucket);
        // 文件夹非空检查：以 '/' 结尾视为文件夹，若有子内容则拒绝删除
        if (path.endsWith("/")) {
            List<FileItem> children = minioService.listObjects(b, path, false);
            if (!children.isEmpty()) {
                throw new IllegalStateException("Folder is not empty, cannot delete");
            }
        }
        // 一并删除该对象的所有分享链接
        shareService.deleteByObject(b, path);
        minioService.deleteObject(b, path);
        return new JSON<>("deleted");
    }

    /**
     * 内联预览（图片/视频）：与 download 类似但<b>不设 Content-Disposition: attachment</b>，
     * 供 &lt;img&gt;/&lt;video&gt; 的 src 内联显示。
     */
    @GET
    @HttpPath("/file/raw")
    @AUTH
    public BINARY raw(@HttpParam("path") String path,
                      @HttpParam("bucket") String bucket) throws Exception {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        String b = resolveBucket(bucket);
        io.minio.StatObjectResponse stat = minioService.statObject(b, path);
        String contentType = (stat != null && stat.contentType() != null)
                ? stat.contentType() : MimeType.APPLICATION_OCTET_STREAM.getType();
        if (stat != null && stat.size() > 0) {
            ServletHelper.getResponse().setHeader("Content-Length", String.valueOf(stat.size()));
        }
        BINARY result = new BINARY();
        result.setData(minioService.getObject(b, path));
        result.setMimeType(MimeType.ALL);
        result.setRawContentType(contentType);
        return result;
    }

    /** 新建文件夹（写入以 '/' 结尾的 0 字节占位对象）。 */
    @GET
    @HttpPath("/file/mkdir")
    @AUTH
    public JSON<String> mkdir(@HttpParam("path") String path,
                              @HttpParam("bucket") String bucket) throws Exception {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        String b = resolveBucket(bucket);
        minioService.makeFolder(b, path);
        return new JSON<>("created");
    }

    /** 列出所有桶名（仅 admin，用于查看全部用户）。 */
    @GET
    @HttpPath("/file/buckets")
    @AUTH(orRole = "admin")
    public JSON<List<String>> buckets() throws Exception {
        List<String> names = new ArrayList<>();
        for (io.minio.messages.ListAllMyBucketsResult.Bucket bk : minioService.listBuckets()) {
            names.add(bk.name());
        }
        return new JSON<>(names);
    }

    /**
     * 查询 MinIO 集群磁盘用量（总容量/已用/可用/磁盘数）。
     *
     * <p>所有登录用户可见--这是共享 MinIO 集群的物理磁盘剩余空间，非单用户配额。
     * MinIO 不可达时返回全 0（前端见 totalBytes=0 即隐藏用量条）。
     */
    @GET
    @HttpPath("/file/storage")
    @AUTH
    public JSON<DiskUsage> storage() {
        DiskUsage usage = minioService.getDiskUsage();
        if (usage == null) {
            usage = new DiskUsage(0, 0, 0, 0, 0, 0);
        }
        return new JSON<>(usage);
    }

    /**
     * 解析目标 bucket：admin 传入 bucket 参数时使用该桶；否则固定为当前用户本人桶。
     */
    private String resolveBucket(String requestedBucket) {
        User user = userService.currentUser();
        if (StpUtil.hasRole("admin") && requestedBucket != null && !requestedBucket.isBlank()) {
            return requestedBucket;
        }
        if (user == null || user.getBucket() == null || user.getBucket().isBlank()) {
            throw new IllegalStateException("current user has no bucket assigned");
        }
        return user.getBucket();
    }

    private static String joinPath(String folder, String filename) {
        if (folder == null || folder.isBlank() || folder.equals("/")) {
            return filename;
        }
        String f = folder.startsWith("/") ? folder.substring(1) : folder;
        if (!f.endsWith("/")) {
            f = f + "/";
        }
        return f + filename;
    }
}
