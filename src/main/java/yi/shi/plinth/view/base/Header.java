package yi.shi.plinth.view.base;

import j2html.tags.specialized.HeaderTag;
import j2html.tags.specialized.LiTag;

import java.util.Map;

import static j2html.TagCreator.a;
import static j2html.TagCreator.header;
import static j2html.TagCreator.li;
import static j2html.TagCreator.nav;
import static j2html.TagCreator.script;
import static j2html.TagCreator.ul;
import static j2html.TagCreator.div;

/**
 * 顶栏 nav + 移动端 sidenav。
 */
public class Header {

    public static HeaderTag createHeader(String color) {
        Map<String, String> menu = Menu.getMenu();
        String navColor = (color == null || color.isEmpty()) ? "blue" : color;
        LiTag[] items = buildItems(menu);
        return header(
                nav().withClass(navColor).with(
                        div().withClass("nav-wrapper container").with(
                                a("MinIO Shell").withClass("brand-logo").withHref("/"),
                                a("Menu").withClass("sidenav-trigger right").withHref("#").withData("target", "side-nav"),
                                ul().withClass("right hide-on-med-and-down").with(items)
                        )
                ),
                ul().withId("side-nav").withClass("sidenav").with(items),
                script().withSrc("/js/SideNav.js?v=" + yi.shi.plinth.App.START_TIME)
        );
    }

    private static LiTag[] buildItems(Map<String, String> menu) {
        return menu.entrySet().stream()
                .map(e -> li(a(e.getKey()).withHref(e.getValue())))
                .toArray(LiTag[]::new);
    }
}
