package yi.shi.plinth.user;

import cn.dev33.satoken.stp.StpUtil;
import com.google.inject.Inject;
import yi.shi.plinth.db.entity.User;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.HttpBody;
import yi.shi.plinth.annotation.http.HttpParam;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.annotation.http.Method.POST;
import yi.shi.plinth.http.result.JSON;
import yi.shi.plinth.servlet.ServletHelper;
import yi.shi.plinth.user.dto.ChangePasswordRequest;
import yi.shi.plinth.user.dto.LoginRequest;
import yi.shi.plinth.user.dto.RegisterRequest;
import yi.shi.plinth.user.dto.UpdateRolesRequest;

/**
 * 用户模块 HTTP 接口。
 *
 * <p>注意：框架 {@code @HttpParam} 透传 {@code request.getParameter()} 的 String，
 * 不做类型转换，故 query 参数一律用 String 接收，在 service 内转换。
 */
@HttpService
public class UserApi {

    @Inject
    private UserService userService;

    @POST
    @HttpPath("/user/register")
    public JSON<User> register(@HttpBody RegisterRequest req) {
        return new JSON<>(userService.register(req.getUsername(), req.getPassword(), req.getRoles()));
    }

    @POST
    @HttpPath("/user/login")
    public JSON<java.util.Map<String, String>> login(@HttpBody LoginRequest req) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        result.put("token", userService.login(req.getUsername(), req.getPassword()));
        return new JSON<>(result);
    }

    @GET
    @HttpPath("/user/logout")
    public JSON<String> logout() throws java.io.IOException {
        userService.logout();
        ServletHelper.getResponse().sendRedirect("/page/login");
        return new JSON<>("logged out");
    }

    @GET
    @HttpPath("/user/current")
    @AUTH
    public JSON<User> current() {
        return new JSON<>(userService.currentUser());
    }

    @GET
    @HttpPath("/user/list")
    @AUTH(orRole = "admin")
    public JSON<PageResult<User>> list(@HttpParam("page") String page, @HttpParam("size") String size) {
        return new JSON<>(userService.list(parseInt(page, 1), parseInt(size, 10)));
    }

    @GET
    @HttpPath("/user/get")
    @AUTH(orRole = "admin")
    public JSON<User> get(@HttpParam("id") String id) {
        return new JSON<>(userService.get(Long.parseLong(id)));
    }

    @POST
    @HttpPath("/user/roles")
    @AUTH(orRole = "admin")
    public JSON<User> updateRoles(@HttpBody UpdateRolesRequest req) {
        return new JSON<>(userService.updateRoles(req.getId(), req.getRoles()));
    }

    @POST
    @HttpPath("/user/password")
    @AUTH
    public JSON<String> changePassword(@HttpBody ChangePasswordRequest req) {
        userService.changePassword(StpUtil.getLoginIdAsLong(), req.getOldPassword(), req.getNewPassword());
        return new JSON<>("password changed");
    }

    /** 重新生成当前用户的 S3 access key / secret（旧密钥立即失效）。 */
    @POST
    @HttpPath("/user/key")
    @AUTH
    public JSON<User> regenerateKey() {
        return new JSON<>(userService.regenerateAccessKey(StpUtil.getLoginIdAsLong()));
    }

    @GET
    @HttpPath("/user/delete")
    @AUTH(orRole = "admin")
    public JSON<String> delete(@HttpParam("id") String id) {
        userService.delete(Long.parseLong(id));
        return new JSON<>("deleted");
    }

    /** 管理员重置用户密码为 123456。 */
    @GET
    @HttpPath("/user/reset-password")
    @AUTH(orRole = "admin")
    public JSON<String> resetPassword(@HttpParam("id") String id) {
        userService.resetPassword(Long.parseLong(id));
        return new JSON<>("password reset to 123456");
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
