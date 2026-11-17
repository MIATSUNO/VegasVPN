package br.com.vegas.independente;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/** Debug-only policy candidate. This does not authenticate an individual volunteer. */
public final class ExperimentalGatePolicy {
    public static final String ROOT_SHA256 = "96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6";
    public static final String NAME = "opengw.net";
    private ExperimentalGatePolicy() {}
    public static String hash(String encoded) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(ProfileInspection.decodeStrict(encoded)));
    }
    private static String hex(byte[] data) {
        StringBuilder s = new StringBuilder(); for (byte b : data) s.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return s.toString();
    }
    public static VpnGateProfileValidator.ParsedProfile validate(boolean debug, String encoded, String ip) {
        if (!debug) return null;
        try {
            VpnGateProfileValidator.ParsedProfile p = VpnGateProfileValidator.validate(encoded, ip).profile;
            if (p == null || !"AES-128-CBC".equals(p.cipher) || !"AES-128-CBC".equals(p.dataCiphers) || !"SHA1".equals(p.auth)) return null;
            if (p.serverName != null && !NAME.equals(p.serverName)) return null;
            String body = p.caPem.replace("-----BEGIN CERTIFICATE-----", "").replace("-----END CERTIFICATE-----", "").replaceAll("\\s", "");
            byte[] der = ProfileInspection.decodeStrict(body);
            if (!ROOT_SHA256.equals(hex(MessageDigest.getInstance("SHA-256").digest(der)))) return null;
            X509Certificate ca = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
            ca.checkValidity();
            return p;
        } catch (Exception e) { return null; }
    }
    /** Exact decoded profile bytes; equal hashes alone do not authorize without same selected row. */
    public static boolean consentMatches(String approvedHash, String encoded) {
        try { return approvedHash != null && MessageDigest.isEqual(approvedHash.getBytes(StandardCharsets.US_ASCII), hash(encoded).getBytes(StandardCharsets.US_ASCII)); }
        catch (Exception e) { return false; }
    }
}
