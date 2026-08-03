package yi.shi.plinth.view.pages;

import j2html.tags.specialized.HtmlTag;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.http.result.HTML;
import yi.shi.plinth.view.base.Head;

import static j2html.TagCreator.a;
import static j2html.TagCreator.body;
import static j2html.TagCreator.div;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.html;
import static j2html.TagCreator.p;

@HttpService
public class NotFoundPage {

    @GET
    @HttpPath("/page/404")
    public HTML notFound() throws Exception {
        HtmlTag htmlTag = html(
                Head.createHead("404"),
                body().withClass("blue").with(
                        div().withClass("container").withStyle("margin-top:80px;text-align:center;").with(
                                h1("404").withClass("white-text"),
                                p("Page not found").withClass("white-text"),
                                a("Go Home").withClass("btn white black-text").withHref("/")
                        )
                )
        );
        HTML result = new HTML();
        result.setHtmlContent(htmlTag.render());
        return result;
    }
}
