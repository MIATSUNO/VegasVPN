import br.com.vegas.independente.VpnGateCatalog;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

public class CatalogTest {
  static final String H="*vpn_servers\n#HostName,IP,CountryLong,CountryShort,Operator,LogType,Ping,Speed,OpenVPN_ConfigData_Base64\n";
  static final String R="host,1.2.3.4,Japan,JP,\"Volunteer, Inc\",2weeks,10,1234,ignored\n";
  static void check(boolean b) { if (!b) throw new AssertionError(); }
  static void rejects(String s) throws Exception { try { VpnGateCatalog.parse(s); throw new AssertionError("expected rejection"); } catch (IOException expected) {} }
  public static void main(String[] args) throws Exception {
    var a=VpnGateCatalog.parse(H+R+"*\n"); check(a.size()==1); check(a.get(0).operator.equals("Volunteer, Inc")); check(a.get(0).address.equals("1.2.3.4"));
    check(a.get(0).inspection == br.com.vegas.independente.ProfileInspection.Summary.UNREADABLE);
    check(a.get(0).screeningReasons.contains(br.com.vegas.independente.VpnGateProfileValidator.Reason.ENGINE_UNAVAILABLE));
    check(a.get(0).screeningReasons.contains(br.com.vegas.independente.VpnGateProfileValidator.Reason.INVALID_ENCODING));
    try { a.get(0).screeningReasons.clear(); throw new AssertionError("screening must be immutable"); } catch (UnsupportedOperationException expected) {}
    String cert = Files.readString(Path.of("tests/fixtures/triage-test-ca.pem"),StandardCharsets.US_ASCII);
    String plausible = "client\ndev tun\nproto udp\nremote 1.2.3.4 1194\nremote-cert-tls server\n<ca>\n"+cert+"</ca>\n";
    String plausible64=java.util.Base64.getEncoder().encodeToString(plausible.getBytes(StandardCharsets.UTF_8));
    var screened=VpnGateCatalog.parse(H+R.replace("ignored",plausible64)+"*\n").get(0);
    check(screened.screeningReasons.contains(br.com.vegas.independente.VpnGateProfileValidator.Reason.MISSING_FIELD));
    check(screened.screeningReasons.contains(br.com.vegas.independente.VpnGateProfileValidator.Reason.RELAY_IDENTITY_UNVERIFIED));
    check(VpnGateCatalog.parse(H+R.replace("ignored",plausible64).replace("1.2.3.4","1.2.3.5")+"*\n").get(0).screeningReasons.contains(br.com.vegas.independente.VpnGateProfileValidator.Reason.ENDPOINT_MISMATCH));
    String encoded = java.util.Base64.getEncoder().encodeToString("remote-cert-tls server\n".getBytes(StandardCharsets.UTF_8));
    check(VpnGateCatalog.parse(H+R.replace("ignored",encoded)+"*\n").get(0).inspection == br.com.vegas.independente.ProfileInspection.Summary.NO_EXPLICIT_SERVER_CHECK);
    String missingHeader = H.replace(",OpenVPN_ConfigData_Base64", "");
    check(VpnGateCatalog.parse(missingHeader+R.replace(",ignored", "")+"*\n").get(0).inspection == br.com.vegas.independente.ProfileInspection.Summary.UNREADABLE);
    check(VpnGateCatalog.parse(H+R.replace("1.2.3.4","999.2.3.4")+"*\n").isEmpty());
    rejects(H+R); rejects(H+"bad,row\n*\n"); rejects(H+R.replace("\"Volunteer, Inc\"","\"broken")+"*\n");
    rejects("*vpn_servers\n#IP,IP\n*\n");
    var longInput=new StringBuilder(H); for(int i=0;i<150;i++)longInput.append(R); longInput.append("*\n"); check(VpnGateCatalog.parse(longInput.toString()).size()==100);
    if(args.length>0) { String live=Files.readString(Path.of(args[0]),StandardCharsets.UTF_8); var rows=VpnGateCatalog.parse(live); check(!rows.isEmpty()); System.out.println("Live CSV: "+rows.size()+" rendered entries (cap 100); first country="+rows.get(0).code+", log="+rows.get(0).logging); }
    System.out.println("PASS: CSV quoting, invalid IP, truncation, bad row, bad quotes, duplicate header, 100-row cap");
  }
}
