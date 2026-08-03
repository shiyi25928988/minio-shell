package yi.shi.plinth.auth;

import org.junit.jupiter.api.Test;
import yi.shi.plinth.auth.SigV4Util.ParsedAuth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SigV4 单测：
 * <ul>
 *   <li>canonicalRequest 用 AWS 官方 GetObject 示例验证（最强正确性校验）；</li>
 *   <li>canonicalQueryString 排序/编码；</li>
 *   <li>parse 解析 Authorization；</li>
 *   <li>sign/verify 往返 + 错误 secret 拒绝。</li>
 * </ul>
 */
class SigV4UtilTest {

    private static final String EMPTY_SHA = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void canonicalRequestMatchesAwsExample() {
        // AWS 官方 SigV4 GetObject 示例（region us-east-1, service s3）
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", "examplebucket.s3.amazonaws.com");
        headers.put("range", "bytes=0-9");
        headers.put("x-amz-content-sha256", EMPTY_SHA);
        headers.put("x-amz-date", "20130524T000000Z");
        List<String> signed = List.of("host", "range", "x-amz-content-sha256", "x-amz-date");

        String expected = """
                GET
                /test.txt

                host:examplebucket.s3.amazonaws.com
                range:bytes=0-9
                x-amz-content-sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
                x-amz-date:20130524T000000Z

                host;range;x-amz-content-sha256;x-amz-date
                e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855""";
        assertEquals(expected, SigV4Util.canonicalRequest(
                "GET", "/test.txt", "", signed, headers, EMPTY_SHA));
    }

    @Test
    void canonicalQueryStringSortsAndEncodes() {
        assertEquals("", SigV4Util.canonicalQueryString(null));
        assertEquals("", SigV4Util.canonicalQueryString(""));
        // 已排序
        assertEquals("a=1&b=2", SigV4Util.canonicalQueryString("a=1&b=2"));
        // 未排序 -> 排序
        assertEquals("a=1&b=2", SigV4Util.canonicalQueryString("b=2&a=1"));
        // %20 与 + 都归一化为 %20（先解码再编码，避免双重编码）
        assertEquals("prefix=my%20folder", SigV4Util.canonicalQueryString("prefix=my%20folder"));
        assertEquals("prefix=my%20folder", SigV4Util.canonicalQueryString("prefix=my+folder"));
        // 已编码的 % 不被双重编码：%2520 -> 解码 %20 -> 编码 %2520
        assertEquals("k=%2520", SigV4Util.canonicalQueryString("k=%2520"));
    }

    @Test
    void uriEncode() {
        assertEquals("abc-_.~", SigV4Util.uriEncode("abc-_.~", true));
        assertEquals("a%20b", SigV4Util.uriEncode("a b", true));
        assertEquals("a/b", SigV4Util.uriEncode("a/b", false));
        assertEquals("a%2Fb", SigV4Util.uriEncode("a/b", true));
    }

    @Test
    void parseAuthorizationHeader() {
        String auth = "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, "
                + "SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=abc123";
        ParsedAuth p = SigV4Util.parse(auth);
        assertNotNull(p);
        assertEquals("AKIAIOSFODNN7EXAMPLE", p.accessKey());
        assertEquals("20130524T000000Z".substring(0, 8), p.dateShort());
        assertEquals("us-east-1", p.region());
        assertEquals("s3", p.service());
        assertEquals("abc123", p.signature());
        assertTrue(p.signedHeaders().contains("host"));
        assertNull(SigV4Util.parse(null));
        assertNull(SigV4Util.parse("Bearer xxx"));
    }

    @Test
    void signAndVerifyRoundTrip() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", "user-1.localhost");
        headers.put("x-amz-content-sha256", "UNSIGNED-PAYLOAD");
        headers.put("x-amz-date", "20240101T120000Z");
        List<String> signed = List.of("host", "x-amz-content-sha256", "x-amz-date");
        String canonQuery = SigV4Util.canonicalQueryString("list-type=2&prefix=photos/");
        String date = "20240101T120000Z";
        String dateShort = "20240101";

        String authHeader = SigV4Util.sign("GET", "/user-1/", canonQuery, signed, headers,
                "UNSIGNED-PAYLOAD", "AKTEST", "secret123",
                date, dateShort, "us-east-1", "s3");
        ParsedAuth parsed = SigV4Util.parse(authHeader);
        assertNotNull(parsed);

        assertTrue(SigV4Util.verify("GET", "/user-1/", canonQuery, parsed.signedHeaders(), headers,
                "UNSIGNED-PAYLOAD", date, parsed.dateShort(), parsed.region(), parsed.service(),
                "secret123", parsed.signature()));

        // 错误 secret 必须拒绝
        assertFalse(SigV4Util.verify("GET", "/user-1/", canonQuery, parsed.signedHeaders(), headers,
                "UNSIGNED-PAYLOAD", date, parsed.dateShort(), parsed.region(), parsed.service(),
                "wrongSecret", parsed.signature()));

        // 篡改路径必须拒绝
        assertFalse(SigV4Util.verify("GET", "/user-2/", canonQuery, parsed.signedHeaders(), headers,
                "UNSIGNED-PAYLOAD", date, parsed.dateShort(), parsed.region(), parsed.service(),
                "secret123", parsed.signature()));
    }
}
