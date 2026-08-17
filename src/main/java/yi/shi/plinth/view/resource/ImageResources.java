package yi.shi.plinth.view.resource;

import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.MimeType;
import yi.shi.plinth.http.result.BINARY;
import yi.shi.plinth.servlet.ServletHelper;

/**
 * 暴露 /images/*.svg 图标资源(每加一个图片文件加一个方法)。
 */
@HttpService
public class ImageResources {

    @GET
    @HttpPath("/images/download.svg")
    public BINARY downloadSvg() {
        return load("/images/download.svg");
    }

    @GET
    @HttpPath("/images/link.svg")
    public BINARY linkSvg() {
        return load("/images/link.svg");
    }

    @GET
    @HttpPath("/images/delete.svg")
    public BINARY deleteSvg() {
        return load("/images/delete.svg");
    }

    private static BINARY load(String path) {
        // 与 JS 一致不缓存，改图标后浏览器立即拿到新版
        ServletHelper.getResponse().setHeader("Cache-Control", "no-store");
        BINARY result = new BINARY();
        result.setMimeType(MimeType.IMAGE_SVG);
        result.setData(ImageResources.class.getResourceAsStream(path));
        return result;
    }
}
