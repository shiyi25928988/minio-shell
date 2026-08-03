package yi.shi.plinth.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AWS Signature Version 4（SigV4）签名/验签工具。
 *
 * <p>{@link yi.shi.plinth.proxy.MinioProxyServlet} 用它做两件事：
 * <ol>
 *   <li><b>verify</b>：用用户的 secret 校验客户端签名（鉴权，证明持有 secret）；</li>
 *   <li><b>sign</b>：用管理员 MinIO 凭据对"去掉 /s3 前缀后的路径"重新签名后转发给 MinIO。</li>
 * </ol>
 *
 * <p>仅依赖 JDK（{@link Mac} / {@link MessageDigest}）。canonical URI 直接使用原始（已编码）路径，
 * 不二次编码——与 MinIO/S3 的实现一致。payload 哈希取自 {@code x-amz-content-sha256} 头，
 * 故无需读取 body 即可验签/重签，body 透传流式。
 */
public final class SigV4Util {

    private SigV4Util() {
    }

    /**
     * 解析后的 Authorization 头信息。
     *
     * <p>注意：credential scope 只含<b>短日期</b>（YYYYMMDD，即 {@code dateShort}）；
     * 完整的 {@code x-amz-date}（YYYYMMDDTHHMMSSZ）不在 Authorization 头里，需由调用方从请求头读取。
     */
    public record ParsedAuth(String accessKey, String dateShort, String region,
                             String service, List<String> signedHeaders, String signature) {
    }

    /**
     * 解析 {@code Authorization: AWS4-HMAC-SHA256 Credential=.../.../.../s3/aws4_request,
     * SignedHeaders=h1;h2, Signature=hex}。格式不符返回 null。
     */
    public static ParsedAuth parse(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("AWS4-HMAC-SHA256 ")) {
            return null;
        }
        String rest = authHeader.substring("AWS4-HMAC-SHA256 ".length());
        String credential = null;
        String signedHeadersStr = null;
        String signature = null;
        for (String part : rest.split(",\\s*")) {
            if (part.startsWith("Credential=")) {
                credential = part.substring("Credential=".length());
            } else if (part.startsWith("SignedHeaders=")) {
                signedHeadersStr = part.substring("SignedHeaders=".length());
            } else if (part.startsWith("Signature=")) {
                signature = part.substring("Signature=".length());
            }
        }
        if (credential == null || signedHeadersStr == null || signature == null) {
            return null;
        }
        String[] c = credential.split("/");
        if (c.length < 5) {
            return null;
        }
        String accessKey = c[0];
        String dateShort = c[1];
        String region = c[2];
        String service = c[3];
        if (dateShort == null || dateShort.length() != 8) {
            return null;
        }
        List<String> signedHeaders = new ArrayList<>(Arrays.asList(signedHeadersStr.split(";")));
        Collections.sort(signedHeaders);
        return new ParsedAuth(accessKey, dateShort, region, service, signedHeaders, signature);
    }

    /**
     * 由原始 query string 构建规范 query string。
     *
     * <p>先按 {@code application/x-www-form-urlencoded} 解码每个 name/value（{@code +} 与 {@code %20} 都视作空格），
     * 再按 SigV4 规则重新 URI-encode（空格编码为 {@code %20}），按 name 排序。
     * 这样无论客户端用 {@code +} 还是 {@code %20}、是否已编码，都能归一化到同一规范形式，与 MinIO/S3 一致。
     */
    public static String canonicalQueryString(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        List<String> encoded = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            int idx = pair.indexOf('=');
            String rawName = idx >= 0 ? pair.substring(0, idx) : pair;
            String rawValue = idx >= 0 ? pair.substring(idx + 1) : "";
            encoded.add(uriEncode(decode(rawName), true) + "=" + uriEncode(decode(rawValue), true));
        }
        Collections.sort(encoded);
        return String.join("&", encoded);
    }

    private static String decode(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    /** 规范 headers：每个已签名 header（小写名）一行 "name:trim(value)\n"。 */
    public static String canonicalHeaders(List<String> signedHeaders, Map<String, String> headerValues) {
        List<String> sorted = new ArrayList<>(signedHeaders);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (String h : sorted) {
            String v = headerValues.get(h);
            if (v == null) {
                v = "";
            }
            sb.append(h).append(":").append(v.trim()).append("\n");
        }
        return sb.toString();
    }

    /** SignedHeaders 串：小写名按字典序用 ';' 连接。 */
    public static String signedHeadersString(List<String> signedHeaders) {
        List<String> sorted = new ArrayList<>(signedHeaders);
        Collections.sort(sorted);
        return String.join(";", sorted);
    }

    /**
     * 用指定凭据签名，返回完整的 Authorization 头值。
     *
     * @param headerValues 已签名 header 的 name(lowercase)->value 映射（重签时 host 为 MinIO 主机）
     */
    public static String sign(String method, String canonicalUri, String canonicalQuery,
                              List<String> signedHeaders, Map<String, String> headerValues, String payloadHash,
                              String accessKey, String secretKey, String date, String dateShort,
                              String region, String service) {
        String stringToSign = buildStringToSign(method, canonicalUri, canonicalQuery,
                signedHeaders, headerValues, payloadHash, date, dateShort, region, service);
        byte[] signingKey = signingKey(secretKey, dateShort, region, service);
        String signature = hex(hmacSha256(signingKey, stringToSign));
        String credentialScope = dateShort + "/" + region + "/" + service + "/aws4_request";
        return "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=" + signedHeadersString(signedHeaders) + ", Signature=" + signature;
    }

    /**
     * 校验签名：用 secretKey 重算期望签名，与 providedSignature 常量时间比较。
     */
    public static boolean verify(String method, String canonicalUri, String canonicalQuery,
                                 List<String> signedHeaders, Map<String, String> headerValues, String payloadHash,
                                 String date, String dateShort, String region, String service,
                                 String secretKey, String providedSignature) {
        String stringToSign = buildStringToSign(method, canonicalUri, canonicalQuery,
                signedHeaders, headerValues, payloadHash, date, dateShort, region, service);
        byte[] signingKey = signingKey(secretKey, dateShort, region, service);
        String expected = hex(hmacSha256(signingKey, stringToSign));
        if (expected == null || providedSignature == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8));
    }

    /** 构建规范请求字符串（包级可见，便于测试）。 */
    static String canonicalRequest(String method, String canonicalUri, String canonicalQuery,
                                   List<String> signedHeaders, Map<String, String> headerValues, String payloadHash) {
        return method + "\n" + canonicalUri + "\n" + canonicalQuery + "\n"
                + canonicalHeaders(signedHeaders, headerValues) + "\n"
                + signedHeadersString(signedHeaders) + "\n" + payloadHash;
    }

    private static String buildStringToSign(String method, String canonicalUri, String canonicalQuery,
                                            List<String> signedHeaders, Map<String, String> headerValues,
                                            String payloadHash, String date, String dateShort,
                                            String region, String service) {
        String canonicalRequest = canonicalRequest(method, canonicalUri, canonicalQuery,
                signedHeaders, headerValues, payloadHash);
        String hashedCanonicalRequest = hex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        String credentialScope = dateShort + "/" + region + "/" + service + "/aws4_request";
        return "AWS4-HMAC-SHA256\n" + date + "\n" + credentialScope + "\n" + hashedCanonicalRequest;
    }

    private static byte[] signingKey(String secretKey, String dateShort, String region, String service) {
        byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateShort);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** RFC 3986 URI-encode；encodeSlash=false 时保留 '/'（path 用），true 时编码 '/'（query 用）。 */
    public static String uriEncode(String s, boolean encodeSlash) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            int ch = b & 0xFF;
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                    || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                sb.append((char) ch);
            } else if (ch == '/' && !encodeSlash) {
                sb.append('/');
            } else {
                // SigV4 要求 URI 编码的十六进制为大写（%2F 而非 %2f）
                sb.append('%');
                sb.append(Character.toUpperCase(Character.forDigit((ch >> 4) & 0xF, 16)));
                sb.append(Character.toUpperCase(Character.forDigit(ch & 0xF, 16)));
            }
        }
        return sb.toString();
    }
}
