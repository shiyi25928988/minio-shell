package yi.shi.plinth.view.element;

import j2html.tags.specialized.DivTag;

import static j2html.TagCreator.div;

/**
 * 全屏 loading 遮罩(Materialize preloader)。
 */
public class BusyIndicator {

    public static DivTag create() {
        return div().withId("busyIndicator").withClass("preloader-wrapper big active").with(
                div().withClass("spinner-layer spinner-blue-only").with(
                        div().withClass("circle-clipper left").with(div().withClass("circle")),
                        div().withClass("gap-patch").with(div().withClass("circle")),
                        div().withClass("circle-clipper right").with(div().withClass("circle"))
                )
        ).attr("style", "display:none;position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);z-index:9999;");
    }
}
