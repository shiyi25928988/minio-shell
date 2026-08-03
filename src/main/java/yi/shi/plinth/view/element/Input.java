package yi.shi.plinth.view.element;

import j2html.tags.specialized.DivTag;

import static j2html.TagCreator.div;
import static j2html.TagCreator.input;
import static j2html.TagCreator.label;

/**
 * Materialize 输入框工厂。
 */
public class Input {

    public static DivTag textInput(String id, String labelName, String type) {
        return div().withClass("input-field").with(
                input().withId(id).withType(type == null ? "text" : type).withClass("validate"),
                label(labelName).withFor(id)
        );
    }
}
