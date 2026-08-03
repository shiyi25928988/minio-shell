package yi.shi.plinth.view;

import j2html.tags.specialized.BodyTag;
import j2html.tags.specialized.FooterTag;
import j2html.tags.specialized.HeadTag;
import j2html.tags.specialized.HeaderTag;
import j2html.tags.specialized.HtmlTag;
import j2html.tags.specialized.MainTag;
import yi.shi.plinth.view.base.Footer;
import yi.shi.plinth.view.base.Header;
import yi.shi.plinth.view.element.BusyIndicator;

import static j2html.TagCreator.body;
import static j2html.TagCreator.html;
import static j2html.TagCreator.script;
import static j2html.TagCreator.style;

/**
 * 抽象页面基类（模板方法）：head + body(busyIndicator + Init.js + header + main + footer)。
 * 子类实现 createHead()/createMain()；可重写 createHeader()/createFooter() 返回 null 做无 chrome 页。
 */
public abstract class Page {

    protected String themeColor = "blue";

    protected void setThemeColor(String themeColor) {
        this.themeColor = themeColor;
    }

    protected HtmlTag createHtml() throws Exception {
        return html(createHead(), createBody());
    }

    protected abstract HeadTag createHead() throws Exception;

    protected HeaderTag createHeader() throws Exception {
        return Header.createHeader(themeColor);
    }

    protected abstract MainTag createMain() throws Exception;

    protected FooterTag createFooter() throws Exception {
        return Footer.createFooter(themeColor);
    }

    protected BodyTag createBody() throws Exception {
        return body().with(
                BusyIndicator.create(),
                script().withSrc("/js/Init.js?v=" + yi.shi.plinth.App.START_TIME),
                createHeader(),
                createMain(),
                createFooter()
        ).with(style("body{display:flex;min-height:100vh;flex-direction:column;} main{flex:1 0 auto;}"));
    }
}
