package yi.shi.plinth.http;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import jakarta.servlet.http.HttpServletResponse;
import yi.shi.plinth.exception.UnsupportMIMETypeException;
import yi.shi.plinth.http.result.BINARY;
import yi.shi.plinth.http.result.JSON;
import yi.shi.plinth.http.result.ReturnType;
import yi.shi.plinth.servlet.ServletHelper;
import yi.shi.plinth.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

/**
 * @author yshi
 */
@Slf4j
public final class HttpRespHelper {

    /**
     * To prevent instantiated.
     */
    private HttpRespHelper() {
        throw new RuntimeException();
    }

    /**
     * @param data
     * @throws UnsupportMIMETypeException
     * @throws IOException
     */
    public static void sendResponseData(Object data, int httpStatusCode) throws IOException {
        if (Objects.nonNull(data)) {
            if (data instanceof ReturnType) {
                sendResponseData((ReturnType<?>) data, httpStatusCode);
            } else {
                sendResponseData(new JSON(data), httpStatusCode);
            }
        } else {
            log.error("RestHelper#sendResponseData send a null object, cause of a void return type method");
            throw new NullPointerException();
        }
    }

    /**
     * @param responseData
     * @throws IOException
     */
    private static void sendResponseData(final ReturnType<?> responseData, final int httpStatusCode) throws IOException {
        HttpServletResponse resp = ServletHelper.getResponse();
        String data = "";

        if (responseData.getData() instanceof String) {
            data = (String) responseData.getData();
        } else if(responseData.getData() instanceof InputStream){
            // MinIO 对象等任意 content-type：BINARY.rawContentType 优先于枚举
            String contentType = responseData.getMimeType().getType();
            if (responseData instanceof BINARY) {
                String raw = ((BINARY) responseData).getRawContentType();
                if (raw != null && !raw.isBlank()) {
                    contentType = raw;
                }
            }
            resp.setStatus(httpStatusCode);
            resp.setCharacterEncoding("UTF-8");
            resp.setContentType(contentType);
            // 用 try-with-resources 关闭输入流：MinIO GetObjectResponse 持有 HTTP 连接，不关闭会泄漏
            try (InputStream in = (InputStream) responseData.getData()) {
                IOUtils.copy(in, resp.getOutputStream());
            }
            resp.getOutputStream().flush();
            resp.getOutputStream().close();
            resp.flushBuffer();
            return;
        } else {
            try {
                data = JsonUtils.toJson(responseData.getData());
            } catch (Exception e) {
                log.error(e.getLocalizedMessage());
                resp.setStatus(HttpStatusCode.SC_INTERNAL_SERVER_ERROR);
                resp.setCharacterEncoding("UTF-8");
                PrintWriter writer = resp.getWriter();
                writer.write("Internal Service Error : " + e.getLocalizedMessage());
                writer.flush();
                writer.close();
                return;
            }
        }
        resp.setStatus(httpStatusCode);
        resp.setContentType(responseData.getMimeType().getType());
        resp.setCharacterEncoding("UTF-8");
        PrintWriter writer = resp.getWriter();
        writer.write(data);
        writer.flush();
        writer.close();
    }

    /**
     * 设置 Content-Disposition: attachment，文件名按 RFC 5987 用 UTF-8 编码，
     * 兼容中文等非 ASCII 字符（同时给老浏览器留 ASCII fallback）。
     */
    public static void setContentDisposition(String filename) {
        if (filename == null || filename.isBlank()) {
            filename = "download";
        }
        String asciiFallback = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        String encoded = encodeFilename(filename);
        ServletHelper.getResponse().setHeader("Content-Disposition",
                "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded);
    }

    /** RFC 5987 percent-encoding（UTF-8，十六进制大写，空格为 %20 而非 +）。 */
    private static String encodeFilename(String s) {
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            int ch = b & 0xFF;
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                    || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                sb.append((char) ch);
            } else {
                sb.append('%');
                sb.append(Character.toUpperCase(Character.forDigit((ch >> 4) & 0xF, 16)));
                sb.append(Character.toUpperCase(Character.forDigit(ch & 0xF, 16)));
            }
        }
        return sb.toString();
    }

}
