package yi.shi.plinth.view.base;

import j2html.tags.specialized.HeadTag;
import yi.shi.plinth.view.resource.JqueryResources;
import yi.shi.plinth.view.resource.MaterializeResources;

import static j2html.TagCreator.head;
import static j2html.TagCreator.link;
import static j2html.TagCreator.meta;
import static j2html.TagCreator.script;
import static j2html.TagCreator.title;

/**
 * 公共 <head>：UTF-8、viewport、title、Materialize CSS、jQuery、Materialize JS。
 */
public class Head {

    public static HeadTag createHead(String pageTitle) {
        return head(
                meta().withCharset("UTF-8"),
                meta().withName("viewport").withContent("width=device-width, initial-scale=1"),
                title(pageTitle),
                link().withRel("stylesheet").withHref(MaterializeResources.MATERIALIZE_MIN_CSS),
                script().withSrc(JqueryResources.JQUERY_MIN_JS),
                script().withSrc(MaterializeResources.MATERIALIZE_MIN_JS)
        );
    }
}
