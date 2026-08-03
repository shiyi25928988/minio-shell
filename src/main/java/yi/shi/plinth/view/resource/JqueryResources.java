package yi.shi.plinth.view.resource;

import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.MimeType;
import yi.shi.plinth.http.result.BINARY;

/**
 * 暴露 jQuery(webjars)。
 */
@HttpService
public class JqueryResources {

    public static final String JQUERY_MIN_JS = "/META-INF/resources/webjars/jquery/3.7.1/jquery.min.js";

    @GET
    @HttpPath(JQUERY_MIN_JS)
    public BINARY jqueryMinJs() {
        BINARY result = new BINARY();
        result.setMimeType(MimeType.APPLICATION_JAVASCRIPT);
        result.setData(this.getClass().getResourceAsStream(JQUERY_MIN_JS));
        return result;
    }
}
