package yi.shi.plinth.view.resource;

import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.MimeType;
import yi.shi.plinth.http.result.BINARY;
import yi.shi.plinth.servlet.ServletHelper;

/**
 * 暴露 /js/*.js 自定义脚本(每加一个 JS 文件加一个方法)。
 */
@HttpService
public class JavaScriptAndCss {

    @GET
    @HttpPath("/js/Init.js")
    public BINARY initJs() {
        return load("/js/Init.js");
    }

    @GET
    @HttpPath("/js/SideNav.js")
    public BINARY sideNavJs() {
        return load("/js/SideNav.js");
    }

    @GET
    @HttpPath("/js/Login.js")
    public BINARY loginJs() {
        return load("/js/Login.js");
    }

    @GET
    @HttpPath("/js/Register.js")
    public BINARY registerJs() {
        return load("/js/Register.js");
    }

    @GET
    @HttpPath("/js/Files.js")
    public BINARY filesJs() {
        return load("/js/Files.js");
    }

    @GET
    @HttpPath("/js/Share.js")
    public BINARY shareJs() {
        return load("/js/Share.js");
    }

    @GET
    @HttpPath("/js/Shares.js")
    public BINARY sharesJs() {
        return load("/js/Shares.js");
    }

    @GET
    @HttpPath("/js/Users.js")
    public BINARY usersJs() {
        return load("/js/Users.js");
    }

    @GET
    @HttpPath("/js/Profile.js")
    public BINARY profileJs() {
        return load("/js/Profile.js");
    }

    private static BINARY load(String path) {
        // JS 不缓存：确保浏览器每次拿到最新脚本（避免改了 Files.js 后仍用旧版导致行为不符预期）
        ServletHelper.getResponse().setHeader("Cache-Control", "no-store");
        BINARY result = new BINARY();
        result.setMimeType(MimeType.APPLICATION_JAVASCRIPT);
        result.setData(JavaScriptAndCss.class.getResourceAsStream(path));
        return result;
    }
}
