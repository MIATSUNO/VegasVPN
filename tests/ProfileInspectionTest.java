import br.com.vegas.independente.ProfileInspection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ProfileInspectionTest {
    static String enc(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    static void eq(ProfileInspection.Summary a, ProfileInspection.Summary b) { if(a != b) throw new AssertionError(a+" != "+b); }
    public static void main(String[] args) {
        var unreadable=ProfileInspection.Summary.UNREADABLE;
        var none=ProfileInspection.Summary.NO_EXPLICIT_SERVER_CHECK;
        var found=ProfileInspection.Summary.HAS_SERVER_CHECK;
        eq(ProfileInspection.inspect(null), unreadable); eq(ProfileInspection.inspect(""), unreadable);
        eq(ProfileInspection.inspect("@@=="), unreadable); eq(ProfileInspection.inspect("AA=A"), unreadable);
        eq(ProfileInspection.inspect("AB=="), unreadable); eq(ProfileInspection.inspect("AAAA===="), unreadable);
        eq(ProfileInspection.inspect("/w=="), unreadable); eq(ProfileInspection.inspect("A".repeat(196612)), unreadable);
        eq(ProfileInspection.inspect(enc("remote-cert-tls server\n")), none);
        eq(ProfileInspection.inspect(enc("REMOTE-CERT-TLS    SERVER\r\n")), none);
        eq(ProfileInspection.inspect(enc("verify-x509-name foo name\n")), found);
        eq(ProfileInspection.inspect(enc("peer-fingerprint 11:22\n")), found);
        eq(ProfileInspection.inspect(enc("# remote-cert-tls server\n; verify-x509-name foo\n")), none);
        eq(ProfileInspection.inspect(enc("<ca>\nremote-cert-tls server\n</ca>\n")), none);
        eq(ProfileInspection.inspect(enc("<ca>\n</wrong>\nremote-cert-tls server\n</ca>\n")), unreadable);
        eq(ProfileInspection.inspect(enc("</ca>\nremote-cert-tls server\n")), unreadable);
        eq(ProfileInspection.inspect(enc("<ca>\n<other>\n</other>\nremote-cert-tls server\n</ca>\n")), unreadable);
        eq(ProfileInspection.inspect(enc("remote-cert-tls server\n<ca>\n")), unreadable);
        eq(ProfileInspection.inspect(enc("remote-cert-tls client\nremote-cert-tls\n")), none);
        eq(ProfileInspection.inspect(enc("client\nremote example.invalid 443\n")), none);
        System.out.println("PASS: size, malformed Base64, padding, UTF-8, comments, inline blocks and recognized directives; detection does not validate profiles");
    }
}
