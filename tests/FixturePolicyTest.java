import br.com.vegas.independente.FixturePolicy;
import java.nio.file.*;

public class FixturePolicyTest {
    static void reject(String value) {
        try { FixturePolicy.validateConfig(value); throw new AssertionError("accepted unsafe config"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) throws Exception {
        FixturePolicy.validateConfig(FixturePolicy.CONFIG);
        reject(null); reject(""); reject(FixturePolicy.CONFIG + "script-security 2\nup /tmp/evil\n");
        reject(FixturePolicy.CONFIG + "remote 1.2.3.4 1194\n");
        reject(FixturePolicy.CONFIG.replace("10.0.2.2", "10.0.2.3"));
        reject(FixturePolicy.CONFIG.replace("vegas-fixture-server name", "vegas-fixture-server name-prefix"));
        reject(FixturePolicy.CONFIG.replace("client\n", "client\r\n"));
        reject(FixturePolicy.CONFIG.replace("proto udp", "proto udp\u0000"));
        reject(FixturePolicy.CONFIG.replace("dev tun", "dev tun #comment"));
        reject(FixturePolicy.CONFIG.replace("remote 10.0.2.2 1194", "remote \"10.0.2.2\" 1194"));
        reject(FixturePolicy.CONFIG.replace("remote-cert-tls server\n", ""));
        reject(FixturePolicy.CONFIG.replace("verify-x509-name", "--verify-x509-name"));
        if (args.length == 1) {
            Path dir = Paths.get(args[0]);
            String ca = Files.readString(dir.resolve("ca.pem")), cert = Files.readString(dir.resolve("client.pem")), key = Files.readString(dir.resolve("client.key"));
            FixturePolicy.validateCredentials(ca, cert, key);
            try { FixturePolicy.validateCredentials(ca + ca, cert, key); throw new AssertionError("duplicate CA accepted"); } catch (IllegalArgumentException expected) { }
            try { FixturePolicy.validateCredentials(ca, ca, key); throw new AssertionError("mismatched key accepted"); } catch (Exception expected) { }
            try { FixturePolicy.validateCredentials(ca, cert, key + "up /evil\n"); throw new AssertionError("key injection accepted"); } catch (IllegalArgumentException expected) { }
        }
        System.out.println("PASS: exact fixture allowlist and credentials");
    }
}
