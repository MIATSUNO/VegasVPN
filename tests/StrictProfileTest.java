import br.com.vegas.independente.VpnGateProfileValidator;
import static br.com.vegas.independente.VpnGateProfileValidator.Reason.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;

/** Uses only generated disposable test PKI; never print or save the constructed profiles. */
public class StrictProfileTest {
    static String enc(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    static void check(boolean b,String why) { if(!b) throw new AssertionError(why); }
    static void reject(String profile, VpnGateProfileValidator.Reason reason) {
        var r=VpnGateProfileValidator.validate(enc(profile),"1.2.3.4");
        check(r.profile==null && r.reasons.contains(reason) && !r.mayConnect(),"expected "+reason+" got "+r.reasons);
    }
    public static void main(String[] args) throws Exception {
        Path d=Paths.get(args[0]); String ca=Files.readString(d.resolve("ca.pem")), cert=Files.readString(d.resolve("client.pem"));
        String key=Files.readString(d.resolve("client.key")), rsa=Files.readString(d.resolve("client-rsa-traditional.key"));
        String hdr="client\ndev tun\nproto tcp\nremote 1.2.3.4 1194\ncipher AES-128-CBC\ndata-ciphers AES-128-CBC\nauth SHA1\nresolv-retry infinite\nnobind\npersist-key\npersist-tun\nverb 3\n";
        String p=hdr+"<ca>\n"+ca+"</ca>\n<cert>\n"+cert+"</cert>\n<key>\n"+rsa+"</key>\n";
        var r=VpnGateProfileValidator.validate(enc(p),"1.2.3.4");
        check(r.profile!=null,"realistic profile rejected "+r.reasons);
        check(r.profile.caPem.equals(ca) && r.profile.certPem.equals(cert) && r.profile.keyPem.equals(rsa),"inline material not preserved");
        check(r.reasons.contains(SERVER_ROLE_NOT_SPECIFIED) && r.reasons.contains(RELAY_IDENTITY_UNVERIFIED) && !r.mayConnect(),"must block missing relay identity");
        String id=p.replace("client\n","client\nremote-cert-tls server\nverify-x509-name test-server name\n");
        r=VpnGateProfileValidator.validate(enc(id),"1.2.3.4");
        check(r.profile!=null && r.profile.serverName.equals("test-server") && !r.mayConnect() && r.reasons.contains(RELAY_IDENTITY_UNVERIFIED),"profile-supplied name must not become independent trust");
        check(VpnGateProfileValidator.validate(enc(p.replace(rsa,key)),"1.2.3.4").profile!=null,"PKCS8 RSA rejected");
        check(VpnGateProfileValidator.validate(enc(p.replace("\n","\r\n")),"1.2.3.4").profile!=null,"CRLF rejected");
        String blanks=p.replace("</ca>","\n</ca>").replace("</cert>","\n</cert>").replace("</key>","\n</key>");
        var withBlanks=VpnGateProfileValidator.validate(enc(blanks),"1.2.3.4");
        check(withBlanks.profile!=null && withBlanks.profile.keyPem.equals(rsa+"\n"),"blank line or preservation rejected");
        reject(p+"up /bin/sh\n",UNSUPPORTED_DIRECTIVE);
        reject(p+"<key>\n"+rsa+"</key>\n",DUPLICATE_DIRECTIVE);
        reject(p.replace("</key>","</cert>"),BAD_SYNTAX);
        reject(p.replace("remote 1.2.3.4", "remote 1.2.3.5"),ENDPOINT_MISMATCH);
        reject(p.replace("remote 1.2.3.4", "remote 01.2.3.4"),BAD_SYNTAX);
        reject(p.replace("remote 1.2.3.4 1194", "remote 1.2.3.4 1194 udp"),BAD_SYNTAX);
        reject(p.replace("AES-128-CBC", "none"),BAD_SYNTAX);
        reject(p.replace("<ca>\n"+ca,"<ca>\n"+cert),INVALID_CA);
        reject(p.replace(cert,ca),CLIENT_KEY_MISMATCH);
        reject(p.replace(rsa,rsa+"up evil\n"),INVALID_CLIENT_KEY);
        reject(p.replace("proto tcp", "proto \"tcp\""),BAD_SYNTAX);
        reject(id.replace("test-server name", "test-server name-prefix"),BAD_SYNTAX);
        check(!VpnGateProfileValidator.validate(enc(p),"1.2.3.4").mayConnect(),"must fail closed");
        System.out.println("PASS: profile parse, RSA PKCS1/PKCS8, preserved inline material and fail-closed attack cases");
    }
}
