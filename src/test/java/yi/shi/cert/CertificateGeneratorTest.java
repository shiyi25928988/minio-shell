package yi.shi.cert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yi.shi.plinth.cert.CertificateGenerator;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CertificateGeneratorTest {

    @Test
    void generateForIp(@TempDir Path dir) throws Exception {
        CertificateGenerator.generate("192.168.1.10", dir.toString(), "changeit");

        assertTrue(Files.exists(dir.resolve("keystore.p12")));
        assertTrue(Files.exists(dir.resolve("cert.pem")));
        assertTrue(Files.exists(dir.resolve("key.pem")));

        // keystore 可加载且含私钥
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(dir.resolve("keystore.p12").toFile())) {
            ks.load(in, "changeit".toCharArray());
        }
        assertNotNull(ks.getKey("plinth", "changeit".toCharArray()));

        // 证书 CN 与 SAN 含 IP
        try (FileInputStream in = new FileInputStream(dir.resolve("cert.pem").toFile())) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
            assertEquals("CN=192.168.1.10", cert.getSubjectX500Principal().getName());
            Collection<?> sans = cert.getSubjectAlternativeNames();
            assertNotNull(sans);
            assertTrue(sans.toString().contains("192.168.1.10"));
            cert.verify(cert.getPublicKey());
        }
    }

    @Test
    void generateForDomain(@TempDir Path dir) throws Exception {
        CertificateGenerator.generate("registry.example.com", dir.toString(), "changeit");

        try (FileInputStream in = new FileInputStream(dir.resolve("cert.pem").toFile())) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
            assertEquals("CN=registry.example.com", cert.getSubjectX500Principal().getName());
            Collection<?> sans = cert.getSubjectAlternativeNames();
            assertNotNull(sans);
            assertTrue(sans.toString().contains("registry.example.com"));
        }
    }
}
