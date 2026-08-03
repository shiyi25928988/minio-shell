package yi.shi.plinth.view.pages;

import j2html.tags.specialized.HeadTag;
import j2html.tags.specialized.MainTag;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.http.result.HTML;
import yi.shi.plinth.view.Page;
import yi.shi.plinth.view.base.Head;

import static j2html.TagCreator.a;
import static j2html.TagCreator.div;
import static j2html.TagCreator.h5;
import static j2html.TagCreator.input;
import static j2html.TagCreator.main;
import static j2html.TagCreator.script;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.tr;

@HttpService
public class UsersPage extends Page {

    @GET
    @HttpPath("/page/users")
    @AUTH(orRole = "admin", authUrl = "/page/login")
    public HTML usersPage() throws Exception {
        HTML result = new HTML();
        result.setHtmlContent(createHtml().render());
        return result;
    }

    @Override
    protected HeadTag createHead() throws Exception {
        return Head.createHead("Users");
    }

    @Override
    protected MainTag createMain() throws Exception {
        String bucketPrefix = System.getProperty("minio.bucketPrefix", "user-");
        return main(
                div().withClass("container").with(
                        h5("Users").withStyle("margin-top:20px;"),
                        a("Refresh").withClass("btn blue").withHref("/page/users").withStyle("margin-bottom:10px;"),
                        input().withType("hidden").withId("bucketPrefix").withValue(bucketPrefix),
                        table().withClass("striped").with(
                                thead(tr(th("ID"), th("Username"), th("Roles"), th("Status"), th("Actions"))),
                                tbody().withId("userTableBody")
                        ),
                        script().withSrc("/js/Users.js?v=" + yi.shi.plinth.App.START_TIME)
                )
        ).withClass("grey lighten-4");
    }
}
