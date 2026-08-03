package yi.shi.plinth.view.resource;

import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.MimeType;
import yi.shi.plinth.http.result.BINARY;

/**
 * 暴露 Materialize CSS/JS(webjars)。
 */
@HttpService
public class MaterializeResources {

    public static final String MATERIALIZE_MIN_CSS = "/META-INF/resources/webjars/materializecss/1.0.0/css/materialize.min.css";
    public static final String MATERIALIZE_MIN_JS = "/META-INF/resources/webjars/materializecss/1.0.0/js/materialize.min.js";

    @GET
    @HttpPath(MATERIALIZE_MIN_CSS)
    public BINARY materializeMinCss() {
        BINARY result = new BINARY();
        result.setMimeType(MimeType.TEXT_CSS);
        result.setData(this.getClass().getResourceAsStream(MATERIALIZE_MIN_CSS));
        return result;
    }

    @GET
    @HttpPath(MATERIALIZE_MIN_JS)
    public BINARY materializeMinJs() {
        BINARY result = new BINARY();
        result.setMimeType(MimeType.APPLICATION_JAVASCRIPT);
        result.setData(this.getClass().getResourceAsStream(MATERIALIZE_MIN_JS));
        return result;
    }
}
