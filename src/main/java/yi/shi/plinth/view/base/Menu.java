package yi.shi.plinth.view.base;

import cn.dev33.satoken.stp.StpUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 导航菜单，按登录态/角色动态生成。
 */
public class Menu {

    public static Map<String, String> getMenu() {
        Map<String, String> menu = new LinkedHashMap<>();
        menu.put("Files", "/");
        if (StpUtil.isLogin()) {
            if (StpUtil.hasRole("admin")) {
                menu.put("Users", "/page/users");
            }
            menu.put("Shares", "/page/shares");
            menu.put("Profile", "/page/profile");
            menu.put("Logout", "/user/logout");
        } else {
            menu.put("Login", "/page/login");
            menu.put("Register", "/page/register");
        }
        return menu;
    }
}
