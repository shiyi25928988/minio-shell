package yi.shi.plinth.view.element;

import j2html.tags.specialized.ButtonTag;

import static j2html.TagCreator.button;

/**
 * Materialize 按钮工厂。
 */
public class Button {

    public static ButtonTag create(String text, String color, String id) {
        return button(text).withClass("btn waves-effect waves-light " + (color == null ? "blue" : color))
                .withId(id).withType("submit");
    }
}
