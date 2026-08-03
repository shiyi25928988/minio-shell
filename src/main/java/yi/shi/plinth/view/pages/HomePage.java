package yi.shi.plinth.view.pages;

import j2html.tags.specialized.HeadTag;
import j2html.tags.specialized.MainTag;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.HttpParam;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.result.HTML;
import yi.shi.plinth.view.Page;
import yi.shi.plinth.view.base.Head;

import static j2html.TagCreator.a;
import static j2html.TagCreator.div;
import static j2html.TagCreator.h5;
import static j2html.TagCreator.input;
import static j2html.TagCreator.label;
import static j2html.TagCreator.main;
import static j2html.TagCreator.option;
import static j2html.TagCreator.p;
import static j2html.TagCreator.script;
import static j2html.TagCreator.select;
import static j2html.TagCreator.span;

/**
 * 首页 = 文件浏览器：列出当前用户桶内文件/文件夹，支持上传/下载/删除/建文件夹。
 *
 * <p>admin 可通过 {@code ?bucket=user-<id>} 指定查看其他用户的桶。
 * 前缀（文件夹）导航由 Files.js 维护客户端状态 + AJAX 拉取，不刷新页面。
 */
@HttpService
public class HomePage extends Page {

    private String bucket = "";

    @GET
    @HttpPath("/")
    @AUTH(authUrl = "/page/login")
    public HTML homePage(@HttpParam("bucket") String bucket) throws Exception {
        this.bucket = bucket == null ? "" : bucket;
        HTML result = new HTML();
        result.setHtmlContent(createHtml().render());
        return result;
    }

    @Override
    protected HeadTag createHead() throws Exception {
        return Head.createHead("Files");
    }

    @Override
    protected MainTag createMain() throws Exception {
        return main(
                div().withClass("container").with(
                        div().withClass("valign-wrapper").withStyle("margin-top:20px;margin-bottom:10px;").with(
                                h5("Files").withStyle("margin:0;").withClass("left"),
                                span("Drag & drop to upload").withClass("grey-text hide-on-small-only")
                                        .withStyle("margin-left:12px;font-size:0.85rem;"),
                                div().withClass("right").with(
                                        a("Upload").withClass("btn blue waves-effect waves-light")
                                                .withId("uploadBtn").withStyle("margin-left:8px;"),
                                        a("New Folder").withClass("btn green waves-effect waves-light")
                                                .withId("mkdirBtn").withStyle("margin-left:8px;"),
                                        a("Refresh").withClass("btn grey waves-effect waves-light")
                                                .withId("refreshBtn").withStyle("margin-left:8px;")
                                )
                        ),
                        input().withType("hidden").withId("bucket").withValue(bucket),
                        input().withType("file").withId("fileInput").withStyle("display:none;"),
                        div().withId("breadcrumb").withClass("grey-text text-darken-1").withStyle("margin-bottom:8px;"),
                        div().withId("uploadProgress").withStyle("display:none;margin-bottom:8px;").with(
                                span("0%").withId("uploadProgressText").withClass("grey-text")
                                        .withStyle("font-size:0.8rem;display:block;margin-bottom:2px;"),
                                div().withClass("progress").withStyle("height:8px;").with(
                                        div().withClass("determinate").withId("uploadProgressBar").withStyle("width:0%;")
                                )
                        ),
                        div().withId("fileContainer"),
                        buildShareModal(),
                        buildPreviewModal(),
                        buildConfirmDeleteModal(),
                        buildMkdirModal(),
                        script().withSrc("/js/Files.js?v=" + yi.shi.plinth.App.START_TIME)
                )
        ).withClass("grey lighten-4");
    }

    /** 分享弹窗：设置过期/密码/次数，创建后展示可复制的链接。 */
    private static j2html.tags.specialized.DivTag buildShareModal() {
        return div().withClass("modal").withId("shareModal").with(
                div().withClass("modal-content").with(
                        h5("Share File"),
                        div().withId("shareForm").with(
                                input().withType("hidden").withId("shareFilePath"),
                                div().withClass("input-field").with(
                                        select().withId("shareExpire").with(
                                                option().withText("Permanent").withValue("").attr("selected", "selected"),
                                                option().withText("1 day").withValue("1"),
                                                option().withText("7 days").withValue("7"),
                                                option().withText("30 days").withValue("30")
                                        ),
                                        label("Expiry").withFor("shareExpire")
                                ),
                                div().withClass("input-field").with(
                                        input().withType("text").withId("sharePassword"),
                                        label("Password (optional)").withFor("sharePassword")
                                ),
                                div().withClass("input-field").with(
                                        input().withType("number").withId("shareMaxCount"),
                                        label("Max downloads (optional)").withFor("shareMaxCount")
                                )
                        ),
                        div().withId("shareResult").withStyle("display:none;").with(
                                div().withClass("input-field").with(
                                        input().withType("text").withId("shareUrl").attr("readonly", "readonly"),
                                        label("Share Link").withFor("shareUrl")
                                ),
                                a("Copy Link").withClass("btn blue waves-effect waves-light copy-share-url").withHref("#!")
                        )
                ),
                div().withClass("modal-footer").with(
                        a("Close").withClass("modal-close waves-effect btn grey").withHref("#!"),
                        a("Create").withClass("waves-effect btn blue").withId("createShareBtn").withHref("#!")
                )
        );
    }

    /** 预览弹窗：图片内联显示、视频内联播放。 */
    private static j2html.tags.specialized.DivTag buildPreviewModal() {
        return div().withClass("modal").withId("previewModal")
                .withStyle("width:90%;max-height:90%;").with(
                        div().withClass("modal-content").withStyle("padding:10px;text-align:center;overflow:auto;")
                                .with(div().withId("previewContent")),
                        div().withClass("modal-footer").with(
                                a("Close").withClass("modal-close waves-effect btn grey").withHref("#!")
                        )
                );
    }

    /** 删除确认弹窗：显示待删文件名 + 警告 + 取消/删除按钮。 */
    private static j2html.tags.specialized.DivTag buildConfirmDeleteModal() {
        return div().withClass("modal").withId("confirmDeleteModal").with(
                div().withClass("modal-content").with(
                        h5("Confirm Delete"),
                        p("Delete the following item? This action cannot be undone.").withClass("grey-text"),
                        p().withId("confirmDeleteName")
                                .withClass("card-panel red lighten-4 red-text text-darken-2")
                                .withStyle("word-break:break-all;margin-top:10px;font-weight:bold;")
                ),
                div().withClass("modal-footer").with(
                        a("Cancel").withClass("modal-close waves-effect btn grey").withHref("#!"),
                        a("Delete").withClass("waves-effect btn red").withId("confirmDeleteBtn").withHref("#!")
                )
        );
    }

    /** 新建文件夹弹窗：文件夹名输入框 + 取消/创建按钮。 */
    private static j2html.tags.specialized.DivTag buildMkdirModal() {
        return div().withClass("modal").withId("mkdirModal").with(
                div().withClass("modal-content").with(
                        h5("New Folder"),
                        div().withClass("input-field").with(
                                input().withType("text").withId("mkdirFolderName"),
                                label("Folder name").withFor("mkdirFolderName")
                        )
                ),
                div().withClass("modal-footer").with(
                        a("Cancel").withClass("modal-close waves-effect btn grey").withHref("#!"),
                        a("Create").withClass("waves-effect btn blue").withId("mkdirConfirmBtn").withHref("#!")
                )
        );
    }
}
