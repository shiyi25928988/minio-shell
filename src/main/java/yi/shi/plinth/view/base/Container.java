package yi.shi.plinth.view.base;

import j2html.tags.Tag;
import j2html.tags.specialized.DivTag;

import static j2html.TagCreator.div;

/**
 * 卡片网格容器。
 */
public class Container {

    public static DivTag create(Tag... cards) {
        return div().withClass("container").with(
                div().withClass("row").with(cards)
        );
    }
}
