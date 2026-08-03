package yi.shi.plinth.share;

import cn.dev33.satoken.stp.StpUtil;
import com.google.inject.Inject;
import io.minio.StatObjectResponse;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.HttpBody;
import yi.shi.plinth.annotation.http.HttpParam;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.annotation.http.Method.POST;
import yi.shi.plinth.db.entity.Share;
import yi.shi.plinth.db.entity.User;
import yi.shi.plinth.http.HttpRespHelper;
import yi.shi.plinth.http.MimeType;
import yi.shi.plinth.http.result.BINARY;
import yi.shi.plinth.http.result.JSON;
import yi.shi.plinth.minio.MinioService;
import yi.shi.plinth.servlet.ServletHelper;
import yi.shi.plinth.share.dto.CreateShareRequest;
import yi.shi.plinth.share.dto.ShareInfo;
import yi.shi.plinth.user.UserService;

import java.util.List;

/**
 * 分享 HTTP 接口。
 *
 * <p>create/list/revoke 需登录（{@code @AUTH}）；check/download 为公开（凭 token 访问，无需登录）。
 * 普通用户只能分享自己桶内文件；admin 可通过 {@code bucket} 指定他人桶。
 */
@HttpService
public class ShareApi {

    @Inject
    private ShareService shareService;

    @Inject
    private UserService userService;

    @Inject
    private MinioService minioService;

    @POST
    @HttpPath("/share/create")
    @AUTH
    public JSON<ShareInfo> create(@HttpBody CreateShareRequest req) {
        String bucket = resolveBucket(req.getBucket());
        ShareInfo info = shareService.create(bucket, req.getPath(),
                StpUtil.getLoginIdAsLong(), req.getExpireDays(), req.getPassword(), req.getMaxCount());
        return new JSON<>(info);
    }

    @GET
    @HttpPath("/share/list")
    @AUTH
    public JSON<List<ShareInfo>> list() {
        return new JSON<>(shareService.listByCreator(StpUtil.getLoginIdAsLong()));
    }

    @GET
    @HttpPath("/share/revoke")
    @AUTH
    public JSON<String> revoke(@HttpParam("token") String token) {
        shareService.revoke(StpUtil.getLoginIdAsLong(), token);
        return new JSON<>("revoked");
    }

    /** 公开：预校验 token 有效性与密码（不计数），供访问页下载前校验密码。 */
    @GET
    @HttpPath("/share/check")
    public JSON<ShareInfo> check(@HttpParam("token") String token,
                                 @HttpParam("password") String password) {
        Share share = shareService.checkAccess(token, password);
        return new JSON<>(shareService.toInfo(share));
    }

    /** 公开：消费一次下载并流式回传对象。 */
    @GET
    @HttpPath("/share/download")
    public BINARY download(@HttpParam("token") String token,
                           @HttpParam("password") String password) throws Exception {
        Share share = shareService.consumeDownload(token, password);
        StatObjectResponse stat = minioService.statObject(share.getBucket(), share.getObjectName());
        String contentType = (stat != null && stat.contentType() != null)
                ? stat.contentType() : MimeType.APPLICATION_OCTET_STREAM.getType();
        String filename = (share.getFilename() != null && !share.getFilename().isBlank())
                ? share.getFilename() : "download";
        HttpRespHelper.setContentDisposition(filename);
        if (stat != null && stat.size() > 0) {
            ServletHelper.getResponse().setHeader("Content-Length", String.valueOf(stat.size()));
        }
        BINARY result = new BINARY();
        result.setData(minioService.getObject(share.getBucket(), share.getObjectName()));
        result.setMimeType(MimeType.ALL);
        result.setRawContentType(contentType);
        return result;
    }

    private String resolveBucket(String requested) {
        User user = userService.currentUser();
        if (StpUtil.hasRole("admin") && requested != null && !requested.isBlank()) {
            return requested;
        }
        if (user == null || user.getBucket() == null || user.getBucket().isBlank()) {
            throw new IllegalStateException("current user has no bucket assigned");
        }
        return user.getBucket();
    }
}
