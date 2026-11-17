import br.com.vegas.independente.VpnGateCatalog;
import br.com.vegas.independente.VpnGateProfileValidator;
import java.util.*;
/** HTTPS read-only; prints counts only, never profiles or credentials. */
public class LiveCatalogSmoke {
  public static void main(String[] args) throws Exception {
    var rows=VpnGateCatalog.fetch();
    var counts=new EnumMap<VpnGateProfileValidator.Reason,Integer>(VpnGateProfileValidator.Reason.class);
    for (var s:rows) for(var reason:s.screeningReasons) counts.merge(reason,1,Integer::sum);
    System.out.println("Parsed catalog rows="+rows.size()+"; reason counts="+counts);
    if(rows.isEmpty()) throw new AssertionError("empty catalog");
  }
}
