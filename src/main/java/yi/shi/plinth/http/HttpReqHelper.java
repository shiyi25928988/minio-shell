package yi.shi.plinth.http;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.IOUtils;
import yi.shi.plinth.servlet.ServletHelper;
import yi.shi.plinth.utils.JsonUtils;

import java.io.IOException;

public final class HttpReqHelper {

    /**
     * 读取请求体并按指定类型反序列化。
     * 使用 getContentLengthLong 避免 int 溢出；当 Content-Length 未知（如 chunked 传输）时
     * 按流完整读取，而非直接丢弃请求体。
     *
     * @param clazz
     * @return
     * @throws IOException
     */
    public static Object getRequestPostBody(Class<?> clazz) throws IOException {
        HttpServletRequest request = ServletHelper.getRequest();
        long contentLength = request.getContentLengthLong();
        byte[] buffer;
        if (contentLength < 0) {
            // Content-Length 未知（chunked 传输等），按流完整读取
            buffer = IOUtils.toByteArray(request.getInputStream());
        } else {
            buffer = new byte[(int) contentLength];
            for (int i = 0; i < contentLength; ) {
                int readlen = request.getInputStream().read(buffer, i, (int) (contentLength - i));
                if (readlen == -1) {
                    break;
                }
                i += readlen;
            }
        }
        if (buffer.length == 0) {
            return null;
        }
        return JsonUtils.fromJson(buffer, clazz);
    }
}
