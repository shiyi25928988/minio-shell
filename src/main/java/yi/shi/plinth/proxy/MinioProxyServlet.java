package yi.shi.plinth.proxy;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.InputStreamRequestContent;
import org.eclipse.jetty.client.InputStreamResponseListener;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.http.HttpField;
import yi.shi.plinth.auth.SigV4Util;
import yi.shi.plinth.db.entity.User;
import yi.shi.plinth.db.mapper.UserMapper;
import yi.shi.plinth.modules.ModuleRegister;

import java.io.InputStream;
import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * S3 兼容代理（{@code /s3/*}）：mc / aws-cli 等 S3 客户端可直连。
 *
 * <p>每个用户持有一对应用签发的 access key / secret（见 Profile 页）。代理流程：
 * <ol>
 *   <li>解析客户端 {@code Authorization}（SigV4），取 access key；</li>
 *   <li>按 access key 查到用户，取其 secret；</li>
 *   <li>用用户 secret 对<b>客户端原始路径</b> {@code /s3/<bucket>/<key>} 校验签名（鉴权）；</li>
 *   <li>隔离校验：路径中的 bucket 必须等于该用户桶，否则 403；</li>
 *   <li>用管理员 MinIO 凭据对<b>去掉 /s3 前缀后的路径</b> {@code /<bucket>/<key>} 重签名；</li>
 *   <li>透传 method/query/headers/body 到 {@code minio.endpoint}，流式回传响应。</li>
 * </ol>
 *
 * <p>MinIO 仅见到管理员凭据；隔离在网关层强制。payload 哈希取自 {@code x-amz-content-sha256} 头，
 * body 透传流式，故 signed/unsigned payload 的大文件均支持。
 * <b>不支持</b> {@code STREAMING-AWS4-HMAC-SHA256-PAYLOAD}（aws-chunked 分块签名上传）。
 */
@Slf4j
public class MinioProxyServlet extends HttpServlet {

    private static final HttpClient httpClient = createClient();

    private static HttpClient createClient() {
        HttpClient client = new HttpClient();
        client.setConnectTimeout(10_000L);
        client.setIdleTimeout(300_000L);
        try {
            client.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start S3 proxy HttpClient", e);
        }
        return client;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws java.io.IOException {
        String endpoint = System.getProperty("minio.endpoint");
        String adminAccessKey = System.getProperty("minio.accessKey");
        String adminSecretKey = System.getProperty("minio.secretKey");
        if (endpoint == null || endpoint.isBlank() || adminAccessKey == null || adminSecretKey == null) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "minio.* not configured");
            return;
        }

        // 1. 解析 Authorization
        String authHeader = req.getHeader("Authorization");
        SigV4Util.ParsedAuth parsed = SigV4Util.parse(authHeader);
        if (parsed == null) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "missing or invalid AWS SigV4 Authorization");
            return;
        }

        // 2. access key -> 用户
        UserMapper userMapper = ModuleRegister.getInjector().getInstance(UserMapper.class);
        User user = userMapper.findByAccessKey(parsed.accessKey());
        if (user == null || user.getMinioSecretKey() == null) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "unknown access key");
            return;
        }

        // 3. payload hash（必须存在；拒绝 aws-chunked 分块签名）+ 完整日期（x-amz-date）
        String payloadHash = req.getHeader("x-amz-content-sha256");
        if (payloadHash == null || payloadHash.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "x-amz-content-sha256 header required");
            return;
        }
        if (payloadHash.equalsIgnoreCase("STREAMING-AWS4-HMAC-SHA256-PAYLOAD")) {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
                    "aws-chunked signed uploads are not supported; use unsigned payload");
            return;
        }
        String amzDate = req.getHeader("x-amz-date");
        if (amzDate == null || amzDate.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "x-amz-date header required");
            return;
        }

        // 客户端原始路径（contextPath 为 "/" -> 无前缀）
        String clientPath = req.getRequestURI();
        String ctx = req.getContextPath();
        if (ctx != null && !ctx.isEmpty() && !ctx.equals("/") && clientPath.startsWith(ctx)) {
            clientPath = clientPath.substring(ctx.length());
        }
        String canonicalQuery = SigV4Util.canonicalQueryString(req.getQueryString());

        // 收集已签名 header 的值（客户端视角）
        Map<String, String> clientHeaders = new HashMap<>();
        for (String h : parsed.signedHeaders()) {
            clientHeaders.put(h, req.getHeader(h));
        }

        // 4. 校验客户端签名（鉴权）
        boolean ok = SigV4Util.verify(req.getMethod(), clientPath, canonicalQuery,
                parsed.signedHeaders(), clientHeaders, payloadHash,
                amzDate, parsed.dateShort(), parsed.region(), parsed.service(),
                user.getMinioSecretKey(), parsed.signature());
        if (!ok) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "signature verification failed");
            return;
        }

        // 5. 隔离校验 + 计算 MinIO 路径
        String minioPath = clientPath.startsWith("/s3") ? clientPath.substring(3) : clientPath;
        if (minioPath.isEmpty()) {
            minioPath = "/";
        }
        String bucket = firstSegment(minioPath);
        if (bucket == null) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "list-buckets is not supported; specify your bucket");
            return;
        }
        if (!bucket.equals(user.getBucket())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "access denied to bucket: " + bucket);
            return;
        }

        // 6. 用管理员凭据对 MinIO 路径重签名（Host 换成 MinIO 主机）
        String minioHost = minioHost(endpoint);
        Map<String, String> minioHeaders = new LinkedHashMap<>(clientHeaders);
        minioHeaders.put("host", minioHost);
        String authForMinio = SigV4Util.sign(req.getMethod(), minioPath, canonicalQuery,
                parsed.signedHeaders(), minioHeaders, payloadHash,
                adminAccessKey, adminSecretKey, amzDate, parsed.dateShort(),
                parsed.region(), parsed.service());

        // 7. 透传到 MinIO
        String target = endpoint.replaceAll("/+$", "") + minioPath;
        if (req.getQueryString() != null && !req.getQueryString().isEmpty()) {
            target += "?" + req.getQueryString();
        }
        try {
            Request request = httpClient.newRequest(target).method(req.getMethod());
            copyRequestHeaders(req, request);
            request.headers(h -> h.put("Authorization", authForMinio));
            if (hasBody(req.getMethod())) {
                request.body(new InputStreamRequestContent(req.getInputStream()));
            }
            InputStreamResponseListener listener = new InputStreamResponseListener();
            request.send(listener);
            Response response = listener.get(60, TimeUnit.SECONDS);

            resp.setStatus(response.getStatus());
            copyResponseHeaders(response, resp);
            try (InputStream in = listener.getInputStream()) {
                in.transferTo(resp.getOutputStream());
            }
        } catch (Exception e) {
            log.error("S3 proxy error forwarding {} {}", req.getMethod(), target, e);
            if (!resp.isCommitted()) {
                resp.sendError(HttpServletResponse.SC_BAD_GATEWAY, "proxy error: " + e.getMessage());
            }
        }
    }

    /** MinIO 主机（含端口），用于重签时 Host header 与 MinIO 实际收到的一致。 */
    private static String minioHost(String endpoint) {
        try {
            URI uri = new URI(endpoint);
            String host = uri.getHost();
            if (host == null) {
                return endpoint;
            }
            int port = uri.getPort();
            return port == -1 ? host : host + ":" + port;
        } catch (Exception e) {
            return endpoint;
        }
    }

    private static String firstSegment(String minioPath) {
        String p = minioPath.startsWith("/") ? minioPath.substring(1) : minioPath;
        if (p.isEmpty()) {
            return null;
        }
        int slash = p.indexOf('/');
        return slash >= 0 ? p.substring(0, slash) : p;
    }

    private static void copyRequestHeaders(HttpServletRequest req, Request request) {
        for (Enumeration<String> names = req.getHeaderNames(); names.hasMoreElements(); ) {
            String name = names.nextElement();
            if (isHopByHop(name) || "content-length".equalsIgnoreCase(name)
                    || "host".equalsIgnoreCase(name) || "authorization".equalsIgnoreCase(name)) {
                continue;
            }
            for (Enumeration<String> values = req.getHeaders(name); values.hasMoreElements(); ) {
                request.headers(h -> h.add(name, values.nextElement()));
            }
        }
    }

    private static void copyResponseHeaders(Response response, HttpServletResponse resp) {
        for (HttpField f : response.getHeaders()) {
            String name = f.getName();
            if (isHopByHop(name) || "transfer-encoding".equalsIgnoreCase(name)
                    || "content-length".equalsIgnoreCase(name)) {
                continue;
            }
            resp.addHeader(name, f.getValue());
        }
    }

    private static boolean hasBody(String method) {
        String m = method.toUpperCase(Locale.ROOT);
        return "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m);
    }

    private static boolean isHopByHop(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("connection") || lower.equals("keep-alive") || lower.equals("proxy-authenticate")
                || lower.equals("proxy-authorization") || lower.equals("te") || lower.equals("trailer")
                || lower.equals("upgrade");
    }
}
