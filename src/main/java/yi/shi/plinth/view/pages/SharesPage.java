package yi.shi.plinth.view.pages;

import j2html.tags.specialized.HeadTag;
import j2html.tags.specialized.MainTag;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.result.HTML;
import yi.shi.plinth.view.Page;
import yi.shi.plinth.view.base.Head;

import static j2html.TagCreator.a;
import static j2html.TagCreator.div;
import static j2html.TagCreator.h5;
import static j2html.TagCreator.main;
import static j2html.TagCreator.script;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.tr;

/**
 * 我的分享列表页（{@code @AUTH}）：{@code GET /page/shares}。
 * 列出当前用户创建的分享，支持复制链接 / 撤销。数据由 {@code /js/Shares.js} 拉 {@code /share/list}。
 */
@HttpService
public class SharesPage extends Page {

    @GET
    @HttpPath("/page/shares")
    @AUTH(authUrl = "/page/login")
    public HTML sharesPage() throws Exception {
        HTML result = new HTML();
        result.setHtmlContent(createHtml().render());
        return result;
    }

    @Override
    protected HeadTag createHead() throws Exception {
        return Head.createHead("My Shares");
    }

    @Override
    protected MainTag createMain() throws Exception {
        return main(
                div().withClass("container").with(
                        h5("My Shares").withStyle("margin-top:20px;"),
                        a("Refresh").withClass("btn blue").withHref("/page/shares").withStyle("margin-bottom:10px;"),
                        table().withClass("striped").with(
                                thead(tr(th("File"), th("Link"), th("Expires"), th("Password"),
                                        th("Downloads"), th("Created"), th("Actions"))),
                                tbody().withId("shareTableBody")
                        ),
                        script().withSrc("/js/Shares.js?v=" + yi.shi.plinth.App.START_TIME)
                )
        ).withClass("grey lighten-4");
    }
}
