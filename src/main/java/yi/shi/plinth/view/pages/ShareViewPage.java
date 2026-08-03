package yi.shi.plinth.view.pages;

import j2html.tags.specialized.HtmlTag;
import com.google.inject.Inject;
import yi.shi.plinth.annotation.http.HttpParam;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.db.entity.Share;
import yi.shi.plinth.http.result.HTML;
import yi.shi.plinth.share.ShareService;
import yi.shi.plinth.view.base.Head;
import yi.shi.plinth.view.element.Button;

import java.time.LocalDateTime;

import static j2html.TagCreator.body;
import static j2html.TagCreator.div;
import static j2html.TagCreator.h6;
import static j2html.TagCreator.html;
import static j2html.TagCreator.input;
import static j2html.TagCreator.p;
import static j2html.TagCreator.script;
import static j2html.TagCreator.span;

/**
 * 分享访问页（公开，无 {@code @AUTH}）：{@code GET /share/view?token=}。
 *
 * <p>展示文件信息 + 下载按钮（若有密码先输密码）。链接无效/过期时提示。
 * 下载逻辑见 {@code /js/Share.js}（调 {@code /share/check} 校验密码后 {@code /share/download}）。
 */
@HttpService
public class ShareViewPage {

    @Inject
    private ShareService shareService;

    @GET
    @HttpPath("/share/view")
    public HTML view(@HttpParam("token") String token) {
        Share share = (token == null || token.isBlank()) ? null : shareService.getByToken(token);
        boolean expired = share != null && share.getExpireTime() != null
                && share.getExpireTime().isBefore(LocalDateTime.now());
        boolean valid = share != null && !expired;

        HtmlTag htmlTag = html(
                Head.createHead("Share"),
                body().withClass("blue").with(
                        div().withClass("container").withStyle("margin-top:80px;").with(
                                div().withClass("card").with(
                                        div().withClass("card-content").with(
                                                valid ? shareContent(share) : invalidContent()
                                        )
                                ),
                                script().withSrc("/js/Share.js?v=" + yi.shi.plinth.App.START_TIME)
                        )
                )
        );
        HTML result = new HTML();
        result.setHtmlContent(htmlTag.render());
        return result;
    }

    private j2html.tags.specialized.DivTag shareContent(Share share) {
        boolean hasPassword = share.getPasswordHash() != null;
        return div().with(
                span("File Share").withClass("card-title"),
                input().withType("hidden").withId("shareToken").withValue(share.getToken()),
                input().withType("hidden").withId("shareHasPassword").withValue(String.valueOf(hasPassword)),
                p().with(span().withText("File: ").attr("style", "font-weight:bold;"),
                        span(share.getFilename() == null ? "" : share.getFilename())),
                p().with(span().withText("Size: ").attr("style", "font-weight:bold;"),
                        span(formatSize(share.getSize()))),
                p().with(span().withText("Expires: ").attr("style", "font-weight:bold;"),
                        span(share.getExpireTime() == null ? "Never" : share.getExpireTime().toString())),
                p().with(span().withText("Downloads: ").attr("style", "font-weight:bold;"),
                        span((share.getDownloadCount() == null ? 0 : share.getDownloadCount())
                                + (share.getMaxCount() == null ? "" : " / " + share.getMaxCount()))),
                h6(hasPassword ? "This file is password protected" : "").withClass("grey-text"),
                hasPassword ? input().withType("password").withId("sharePassword")
                        .withClass("center-align").attr("placeholder", "Password")
                        .withStyle("margin:10px auto;display:block;") : div(),
                Button.create("Download", "blue", "downloadBtn")
        );
    }

    private j2html.tags.specialized.DivTag invalidContent() {
        return div().with(
                span("File Share").withClass("card-title"),
                p("This share link is invalid or has expired.").withClass("red-text")
        );
    }

    private static String formatSize(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double size = bytes;
        int i = 0;
        while (size >= 1024 && i < units.length - 1) {
            size /= 1024;
            i++;
        }
        return String.format("%.2f %s", size, units[i]);
    }
}
