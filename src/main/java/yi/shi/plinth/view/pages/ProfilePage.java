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

import static j2html.TagCreator.div;
import static j2html.TagCreator.h5;
import static j2html.TagCreator.input;
import static j2html.TagCreator.main;
import static j2html.TagCreator.script;

@HttpService
public class ProfilePage extends Page {

    @GET
    @HttpPath("/page/profile")
    @AUTH(authUrl = "/page/login")
    public HTML profilePage() throws Exception {
        HTML result = new HTML();
        result.setHtmlContent(createHtml().render());
        return result;
    }

    @Override
    protected HeadTag createHead() throws Exception {
        return Head.createHead("Profile");
    }

    @Override
    protected MainTag createMain() throws Exception {
        String s3Endpoint = System.getProperty("s3.external.endpoint", "");
        return main(
                div().withClass("container").with(
                        h5("Profile").withStyle("margin-top:20px;"),
                        input().withType("hidden").withId("s3Endpoint").withValue(s3Endpoint),
                        div().withId("profileContainer"),
                        script().withSrc("/js/Profile.js?v=" + yi.shi.plinth.App.START_TIME)
                )
        ).withClass("grey lighten-4");
    }
}
