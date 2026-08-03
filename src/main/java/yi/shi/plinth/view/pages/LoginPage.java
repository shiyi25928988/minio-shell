package yi.shi.plinth.view.pages;

import j2html.tags.specialized.HtmlTag;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.result.HTML;
import yi.shi.plinth.view.base.Head;
import yi.shi.plinth.view.element.Button;
import yi.shi.plinth.view.element.Input;

import static j2html.TagCreator.a;
import static j2html.TagCreator.body;
import static j2html.TagCreator.div;
import static j2html.TagCreator.form;
import static j2html.TagCreator.html;
import static j2html.TagCreator.p;
import static j2html.TagCreator.script;
import static j2html.TagCreator.span;

@HttpService
public class LoginPage {

    @GET
    @HttpPath("/page/login")
    public HTML loginPage() throws Exception {
        HtmlTag htmlTag = html(
                Head.createHead("Login"),
                body().withClass("blue").with(
                        div().withClass("container").withStyle("margin-top:80px;").with(
                                div().withClass("card").with(
                                        div().withClass("card-content").with(
                                                span("Login").withClass("card-title").withId("formTitle"),
                                                form().withId("loginForm").with(
                                                        Input.textInput("loginUsername", "Username", "text"),
                                                        Input.textInput("loginPassword", "Password", "password"),
                                                        Button.create("Login", "blue", "loginBtn"),
                                                        p().with(
                                                                span("No account? "),
                                                                a("Register").withHref("#").withId("toRegister")
                                                        )
                                                ),
                                                form().withId("registerForm").withStyle("display:none;").with(
                                                        Input.textInput("regUsername", "Username", "text"),
                                                        Input.textInput("regPassword", "Password", "password"),
                                                        Button.create("Register", "blue", "registerBtn"),
                                                        p().with(
                                                                span("Have account? "),
                                                                a("Login").withHref("#").withId("toLogin")
                                                        )
                                                )
                                        )
                                )
                        ),
                        script().withSrc("/js/Login.js?v=" + yi.shi.plinth.App.START_TIME)
                )
        );
        HTML result = new HTML();
        result.setHtmlContent(htmlTag.render());
        return result;
    }
}
