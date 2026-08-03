package yi.shi.plinth.view.pages;

import j2html.tags.specialized.HtmlTag;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.http.result.HTML;
import yi.shi.plinth.view.base.Head;
import yi.shi.plinth.view.element.Button;
import yi.shi.plinth.view.element.Input;

import static j2html.TagCreator.body;
import static j2html.TagCreator.div;
import static j2html.TagCreator.form;
import static j2html.TagCreator.html;
import static j2html.TagCreator.script;
import static j2html.TagCreator.span;

@HttpService
public class RegisterPage {

    @GET
    @HttpPath("/page/register")
    public HTML registerPage() throws Exception {
        HtmlTag htmlTag = html(
                Head.createHead("Register"),
                body().withClass("blue").with(
                        div().withClass("container").withStyle("margin-top:80px;").with(
                                div().withClass("card").with(
                                        div().withClass("card-content").with(
                                                span("Register").withClass("card-title"),
                                                form().withId("registerForm").with(
                                                        Input.textInput("username", "Username", "text"),
                                                        Input.textInput("password", "Password", "password"),
                                                        Button.create("Register", "blue", "registerBtn")
                                                )
                                        )
                                )
                        ),
                        script().withSrc("/js/Register.js?v=" + yi.shi.plinth.App.START_TIME)
                )
        );
        HTML result = new HTML();
        result.setHtmlContent(htmlTag.render());
        return result;
    }
}
