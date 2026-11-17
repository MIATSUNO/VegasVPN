import br.com.vegas.independente.ExperimentalGatePolicy;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
public class ExperimentalGatePolicyTest {
 static String enc(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
 static void ok(boolean x, String msg) { if (!x) throw new AssertionError(msg); }
 public static void main(String[] args) throws Exception {
  Path d=Path.of(args[0]); String ca=Files.readString(Path.of(args[1])), badca=Files.readString(d.resolve("ca.pem"));
  String cert=Files.readString(d.resolve("client.pem")), key=Files.readString(d.resolve("client.key"));
  String hdr="client\ndev tun\nproto tcp\nremote 1.2.3.4 1194\ncipher AES-128-CBC\ndata-ciphers AES-128-CBC\nauth SHA1\nresolv-retry infinite\nnobind\npersist-key\npersist-tun\nverb 3\n";
  String text=hdr+"<ca>\n"+ca+"</ca>\n<cert>\n"+cert+"</cert>\n<key>\n"+key+"</key>\n";
  String a=enc(text), hash=ExperimentalGatePolicy.hash(a);
  ok(ExperimentalGatePolicy.validate(true,a,"1.2.3.4")!=null,"pinned root allowed for DEBUG policy");
  ok(ExperimentalGatePolicy.validate(false,a,"1.2.3.4")==null,"release blocked");
  ok(ExperimentalGatePolicy.validate(true,enc(text.replace(ca,badca)),"1.2.3.4")==null,"foreign CA blocked");
  ok(ExperimentalGatePolicy.validate(true,enc(text+"up /bin/sh\n"),"1.2.3.4")==null,"script blocked");
  ok(ExperimentalGatePolicy.validate(true,enc(text+"verify-x509-name evil name\n"),"1.2.3.4")==null,"foreign name blocked");
  ok(ExperimentalGatePolicy.validate(true,enc(text+"verify-x509-name opengw.net name\n"),"1.2.3.4")!=null,"matching name accepted");
  ok(ExperimentalGatePolicy.validate(true,a,"1.2.3.5")==null,"IP mismatch blocked");
  ok(ExperimentalGatePolicy.validate(true,enc(text.replace("data-ciphers AES-128-CBC\n","")),"1.2.3.4")==null,"cipher missing blocked");
  ok(ExperimentalGatePolicy.consentMatches(hash,a),"same profile");
  ok(!ExperimentalGatePolicy.consentMatches(hash,enc(text+"#modified\n")),"byte change invalidates consent");
  ok(!ExperimentalGatePolicy.consentMatches(hash,null),"missing profile blocked");
  System.out.println("PASS: debug root/name policy, parser attacks, release rejection, exact-byte consent hash");
 }
}
