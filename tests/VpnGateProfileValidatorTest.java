import br.com.vegas.independente.VpnGateProfileValidator;
import static br.com.vegas.independente.VpnGateProfileValidator.Reason.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;

public class VpnGateProfileValidatorTest {
    static String enc(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    static void check(boolean b, String msg) { if (!b) throw new AssertionError(msg); }
    static void has(String input, String ip, VpnGateProfileValidator.Reason reason) {
        var r = VpnGateProfileValidator.validate(input, ip);
        check(!r.mayConnect(), "must never authorize");
        check(r.reasons.contains(ENGINE_UNAVAILABLE) && r.reasons.contains(RELAY_IDENTITY_UNVERIFIED) && r.reasons.contains(CA_NOT_INDEPENDENTLY_TRUSTED), "missing always-blocking reason");
        check(r.reasons.contains(reason), "missing " + reason + ": " + r.reasons);
    }
    public static void main(String[] args) throws Exception {
        String certificate = java.nio.file.Files.readString(java.nio.file.Path.of("tests/fixtures/triage-test-ca.pem"), StandardCharsets.US_ASCII);
        String plausible = "client\ndev tun\nproto udp\nremote 1.2.3.4 1194\nremote-cert-tls server\n<ca>\n" + certificate + "</ca>\n";
        var screened = VpnGateProfileValidator.validate(enc(plausible), "1.2.3.4");
        check(screened.reasons.contains(MISSING_FIELD) && screened.reasons.contains(ENGINE_UNAVAILABLE) &&
                screened.reasons.contains(RELAY_IDENTITY_UNVERIFIED) && screened.reasons.contains(CA_NOT_INDEPENDENTLY_TRUSTED), "valid syntax still blocked: " + screened.reasons);
        check(!screened.mayConnect(), "valid-looking untrusted profile must not authorize");
        has(enc(plausible.replace("remote 1.2.3.4", "remote 1.2.3.5")), "1.2.3.4", ENDPOINT_MISMATCH);
        has(enc(plausible.replace("</ca>", "</wrong>")), "1.2.3.4", BAD_SYNTAX);
        has(enc(plausible + "up /bin/sh\n"), "1.2.3.4", UNSUPPORTED_DIRECTIVE);
        has(null, "1.2.3.4", INVALID_ENCODING);
        has("A".repeat(196612), "1.2.3.4", TOO_LARGE);
        has("@@==", "1.2.3.4", INVALID_ENCODING);
        has(enc("client\nclient\n"), "1.2.3.4", DUPLICATE_DIRECTIVE);
        has(enc("client\nup /tmp/a\n"), "1.2.3.4", UNSUPPORTED_DIRECTIVE);
        has(enc("remote 1.2.3.5 1194\n"), "1.2.3.4", ENDPOINT_MISMATCH);
        has(enc("remote 1.2.3.4 65536\n"), "1.2.3.4", BAD_SYNTAX);
        has(enc("client\n\0"), "1.2.3.4", INVALID_TEXT);
        has(enc("<ca>\nnot-a-cert\n</ca>\n"), "1.2.3.4", INVALID_CA);
        has(enc("client\n"), "1.2.3.4", MISSING_FIELD);
        Random random = new Random(20261116L);
        for (int i=0; i<2000; i++) {
            byte[] bytes = new byte[random.nextInt(512)]; random.nextBytes(bytes);
            var r = VpnGateProfileValidator.validate(Base64.getEncoder().encodeToString(bytes), "1.2.3.4");
            check(!r.mayConnect() && r.reasons.contains(ENGINE_UNAVAILABLE), "random input authorized");
        }
        System.out.println("PASS: validator rejects risky cases; 2000 deterministic random inputs never authorize");
    }
}
