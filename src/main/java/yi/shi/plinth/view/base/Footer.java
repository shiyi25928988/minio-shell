package yi.shi.plinth.view.base;

import j2html.tags.specialized.FooterTag;

import static j2html.TagCreator.div;
import static j2html.TagCreator.footer;

/**
 * 页脚。
 */
public class Footer {

    public static FooterTag createFooter(String color) {
        String c = (color == null || color.isEmpty()) ? "blue" : color;
        return footer().withClass("page-footer " + c).with(
                div().withClass("footer-copyright container").withText("MinIO Shell © 2026")
        );
    }
}
