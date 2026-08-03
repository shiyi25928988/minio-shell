package yi.shi.plinth.http.result;

import yi.shi.plinth.http.MimeType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BINARY implements ReturnType<InputStream> {

    private InputStream inputStream;

    private MimeType mimeType = MimeType.ALL;

    /** 任意 content-type（如 MinIO 对象的真实类型）；非空时优先于 {@link #mimeType}。 */
    private String rawContentType;

    public BINARY setData(InputStream inputStream) {
        this.inputStream = inputStream;
        return this;
    }

    public BINARY setMimeType(MimeType mimeType){
        this.mimeType = mimeType;
        return this;
    }

    public BINARY setRawContentType(String rawContentType) {
        this.rawContentType = rawContentType;
        return this;
    }

    @Override
    public MimeType getMimeType() {
        return this.mimeType;
    }

    public String getRawContentType() {
        return this.rawContentType;
    }

    @Override
    public InputStream getData() {
        return this.inputStream;
    }
}
