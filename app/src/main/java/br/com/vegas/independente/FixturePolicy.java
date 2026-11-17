package br.com.vegas.independente;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.cert.*;
import java.io.*;
import java.util.*;

/** Only a local, fixed test fixture. Never accepts VPN Gate input. */
public final class FixturePolicy {
    public static final String HOST = "10.0.2.2", SERVER_NAME = "vegas-fixture-server";
    public static final int PORT = 1194;
    public static final String CONFIG = "client\ndev tun\nproto udp\nremote 10.0.2.2 1194\nremote-cert-tls server\nverify-x509-name vegas-fixture-server name\n";
    private FixturePolicy() {}
    public static void validateConfig(String text) {
        if (text == null || text.length() > 2048 || !CONFIG.equals(text))
            throw new IllegalArgumentException("Fixture config is not the exact allowlisted profile");
    }
    private static byte[] pem(String text, String type) {
        if (text == null || text.length() > 16384) throw new IllegalArgumentException("PEM size");
        String pattern = "-----BEGIN " + type + "-----\\n(?:[A-Za-z0-9+/=]+\\n)+-----END " + type + "-----\\n";
        if (!text.matches(pattern)) throw new IllegalArgumentException("PEM format");
        String[] lines = text.split("\\n");
        StringBuilder b = new StringBuilder();
        for (int i=1; i<lines.length-1; i++) b.append(lines[i]);
        return Base64.getDecoder().decode(b.toString());
    }
    private static X509Certificate certificate(String text) throws Exception {
        byte[] raw = pem(text, "CERTIFICATE");
        ByteArrayInputStream in = new ByteArrayInputStream(raw);
        X509Certificate c = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        if (in.available() != 0) throw new IllegalArgumentException("extra certificate data");
        c.checkValidity(); return c;
    }
    public static void validateCredentials(String caPem, String certPem, String keyPem) throws Exception {
        X509Certificate ca = certificate(caPem), cert = certificate(certPem);
        if (ca.getBasicConstraints() < 0) throw new IllegalArgumentException("not a CA");
        ca.verify(ca.getPublicKey());
        cert.verify(ca.getPublicKey());
        byte[] encoded = pem(keyPem, "PRIVATE KEY");
        String algorithm = cert.getPublicKey().getAlgorithm();
        if (!algorithm.equals("RSA") && !algorithm.equals("EC")) throw new IllegalArgumentException("unsupported key");
        PrivateKey key = KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(encoded));
        String sigAlg = algorithm.equals("RSA") ? "SHA256withRSA" : "SHA256withECDSA";
        Signature signer = Signature.getInstance(sigAlg); byte[] challenge = "vegas fixture key match".getBytes(StandardCharsets.US_ASCII);
        signer.initSign(key); signer.update(challenge); byte[] signature = signer.sign();
        signer.initVerify(cert.getPublicKey()); signer.update(challenge);
        if (!signer.verify(signature)) throw new IllegalArgumentException("key does not match client certificate");
    }
}
