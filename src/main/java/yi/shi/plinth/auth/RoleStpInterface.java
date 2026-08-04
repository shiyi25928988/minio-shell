package yi.shi.plinth.auth;

import cn.dev33.satoken.stp.StpInterface;

import java.util.Collections;
import java.util.List;

/**
 * sa-token 角色提供者：从内存 {@link RolesHelper} 读取。
 */
public class RoleStpInterface implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 统一用 String 查询，与 AuthHelper.login 存入的 key 类型一致
        List<String> roles = RolesHelper.getRoles(String.valueOf(loginId));
        return roles != null ? roles : Collections.emptyList();
    }
}
