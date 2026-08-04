package yi.shi.plinth.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Objects;

/**
 * 认证辅助类。角色存内存（{@link RolesHelper}），sa-token 会话走默认内存 DAO。
 */
public final class AuthHelper {

    public static void login(Object user, String...roles){
        // 用 String 作 key：sa-token 回调 RoleStpInterface 时 loginId 是 String，
        // 与此处存入的 Long 不匹配会导致角色读不到，统一 String.valueOf
        RolesHelper.addRoles(String.valueOf(user), Lists.newArrayList(roles));
        StpUtil.login(user);
    }

    public static void login(Object user, List<String> roles){
        RolesHelper.addRoles(String.valueOf(user), roles);
        StpUtil.login(user);
    }

    public static void logout() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        StpUtil.logout();
        if (Objects.nonNull(loginId)) {
            RolesHelper.remove(String.valueOf(loginId));
        }
    }
}
