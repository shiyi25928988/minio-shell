package yi.shi.plinth.http.controller;

import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.http.MimeType;
import yi.shi.plinth.http.result.BINARY;

import yi.shi.plinth.annotation.http.Method.GET;

@HttpService
public class FaviconController {
    @GET
    @HttpPath("/favicon.ico")
    public BINARY getIcon(){
        BINARY binary = new BINARY();
        binary.setData(this.getClass().getResourceAsStream("/icon/favicon-32x32-blue.png"));
        binary.setMimeType(MimeType.IMAGE_ICON);
        return binary;
    }
}
