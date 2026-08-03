package yi.shi.plinth.cert;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.FileWriter;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 自签名 X.509 v3 TLS 证书生成器（Bouncy Castle）。
 *
 * <p>根据 IP 或域名生成 RSA 密钥对 + 自签名证书，SAN 自动判为 iPAddress 或 dNSName，
 * 输出 PKCS12 keystore（供 Jetty HTTPS 使用）与 cert.pem / key.pem（可分发供客户端信任）。
 */
@Slf4j
public final class CertificateGenerator {

    private static final int KEY_SIZE = 2048;
    private static final int VALIDITY_DAYS = 3650;
    private static final String ALIAS = "plinth";
    private static final String KEYSTORE_FILE = "keystore.p12";
    private static final String CERT_FILE = "cert.pem";
    private static final String KEY_FILE = "key.pem";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private CertificateGenerator() {
    }

    /**
     * 生成自签名证书并写入指定目录：keystore.p12 / cert.pem / key.pem。
     *
     * @param host     IP 或域名（写入 CN 与 SAN）
     * @param certDir  输出目录
     * @param password keystore 密码
     */
    public static void generate(String host, String certDir, String password) throws Exception {
        Path dir = Paths.get(certDir);
        Files.createDirectories(dir);

        KeyPair keyPair = generateKeyPair();
        X509Certificate cert = generateCertificate(host, keyPair);

        writeKeystore(dir.resolve(KEYSTORE_FILE), keyPair, cert, password);
        writePem(dir.resolve(CERT_FILE), cert);
        writePem(dir.resolve(KEY_FILE), keyPair.getPrivate());

        log.info("Self-signed certificate generated for host [{}] in {} (keystore.p12, cert.pem, key.pem)", host, dir);
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(KEY_SIZE);
        return kpg.generateKeyPair();
    }

    private static X509Certificate generateCertificate(String host, KeyPair keyPair) throws Exception {
        X500Name name = new X500Name("CN=" + host);
        Instant now = Instant.now();
        BigInteger serial = new BigInteger(64, new SecureRandom());

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, serial,
                Date.from(now),
                Date.from(now.plus(VALIDITY_DAYS, ChronoUnit.DAYS)),
                name,
                keyPair.getPublic());

        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.subjectAlternativeName, false, san(host));

        JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME);
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signerBuilder.build(keyPair.getPrivate())));
        cert.verify(keyPair.getPublic());
        return cert;
    }

    private static GeneralNames san(String host) {
        GeneralName gn = isIpAddress(host)
                ? new GeneralName(GeneralName.iPAddress, host)
                : new GeneralName(GeneralName.dNSName, host);
        return new GeneralNames(gn);
    }

    private static boolean isIpAddress(String host) {
        return host != null
                && (host.matches("^(\\d{1,3}\\.){3}\\d{1,3}$") || host.contains(":"));
    }

    private static void writeKeystore(Path path, KeyPair keyPair, X509Certificate cert, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null);
        ks.setKeyEntry(ALIAS, keyPair.getPrivate(), password.toCharArray(), new Certificate[]{cert});
        try (OutputStream os = Files.newOutputStream(path)) {
            ks.store(os, password.toCharArray());
        }
    }

    private static void writePem(Path path, Object object) throws Exception {
        try (JcaPEMWriter pw = new JcaPEMWriter(new FileWriter(path.toFile()))) {
            pw.writeObject(object);
        }
    }
}
